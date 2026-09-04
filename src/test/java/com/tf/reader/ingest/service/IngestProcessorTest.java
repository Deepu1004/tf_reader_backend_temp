package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.index.BuiltSearchIndex;

/** The background half of ingest: queue draining and the watchdog. */
class IngestProcessorTest {

	private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final CatalogueItemRepository items = mock(CatalogueItemRepository.class);
	private final BookStorage bookStorage = mock(BookStorage.class);
	private final AssetLocker assetLocker = mock(AssetLocker.class);
	private final SearchIndexBuilder searchIndexBuilder = mock(SearchIndexBuilder.class);
	private final CatalogueVersionBumper bumper = mock(CatalogueVersionBumper.class);
	private final IngestProcessor processor = new IngestProcessor(items, bookStorage, assetLocker, searchIndexBuilder,
			bumper, CLOCK, Duration.ofMinutes(15));

	private static CatalogueItem queued(String id, AccessTier tier, ContentType type) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setAccessTier(tier);
		item.setContentType(type);
		item.setContentState(ContentState.QUEUED);
		item.setUpdatedAt(NOW);
		return item;
	}

	@Test
	void openAccessPdfReachesReadyWithNoKeyButStillGetsAPlaintextIndex() {
		CatalogueItem item = queued("item_1", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_1/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(eq("item_1"), eq(ContentType.PDF), any(), any())).thenAnswer(invocation -> {
			CatalogueItem.Asset asset = invocation.getArgument(3);
			asset.setHasSearchIndex(true);
			asset.setIndexTerms(10);
			return Optional.of(new BuiltSearchIndex("index-json".getBytes(), 10));
		});
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(item.getMasterWrappedBek()).isNull();
		assertThat(item.getAssets().get(0).isEncrypted()).isFalse();
		assertThat(item.getIndexKey()).isEqualTo("items/item_1/index");
		verify(bookStorage).store("items/item_1/index", "index-json".getBytes(), "application/json");
		verify(assetLocker, never()).lock(any(), any(), any(), any());
		verify(bumper).bump(CatalogueVersionBumper.Scope.ITEM, "item_1");
	}

	@Test
	void openAccessPdfWithNoTextLayerReachesReadyWithNoIndexAtAll() {
		CatalogueItem item = queued("item_1b", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_1b/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(item.getIndexKey()).isNull();
		verify(bookStorage, never()).store(eq("items/item_1b/index"), any(), any());
	}

	@Test
	void subscriptionPdfIsLockedAndVersionBumped() {
		CatalogueItem item = queued("item_2", AccessTier.SUBSCRIPTION, ContentType.PDF);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setEncrypted(true);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_2/upload")).thenReturn("plain".getBytes());
		when(assetLocker.lock(eq("item_2"), eq(ContentType.PDF), any(), any()))
				.thenReturn(new AssetLocker.Result(asset, "cipher".getBytes(), null, "wrapped-bek"));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(item.getMasterWrappedBek()).isEqualTo("wrapped-bek");
		assertThat(item.getStorageKey()).isEqualTo("items/item_2/content");
		verify(bookStorage).store("items/item_2/content", "cipher".getBytes(), asset.getMimeType());
		verify(bumper).bump(CatalogueVersionBumper.Scope.ITEM, "item_2");
	}

	@Test
	void openAccessAudioIsNeverLockedAndNeverIndexed() {
		CatalogueItem item = queued("item_3", AccessTier.OPEN_ACCESS, ContentType.AUDIO);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_3/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		verify(assetLocker, never()).lock(any(), any(), any(), any());
		assertThat(item.getAssets().get(0).isEncrypted()).isFalse();
		assertThat(item.getIndexKey()).isNull();
		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
	}

	@Test
	void subscriptionAndEliteAudioIsNowLockedLikeAnyOtherFormatButStillNeverIndexed() {
		CatalogueItem item = queued("item_3b", AccessTier.ELITE, ContentType.AUDIO);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setEncrypted(true);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_3b/upload")).thenReturn("plain".getBytes());
		when(assetLocker.lock(eq("item_3b"), eq(ContentType.AUDIO), any(), any()))
				.thenReturn(new AssetLocker.Result(asset, "cipher".getBytes(), null, "wrapped-bek"));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(item.getMasterWrappedBek()).isEqualTo("wrapped-bek");
		assertThat(item.getAssets().get(0).isEncrypted()).isTrue();
		assertThat(item.getIndexKey()).isNull();
		verify(bookStorage).store("items/item_3b/content", "cipher".getBytes(), asset.getMimeType());
	}

	@Test
	void audioUsesTheRealUploadedMimeTypeNotAHardcodedGuess() {
		CatalogueItem item = queued("item_audio", AccessTier.OPEN_ACCESS, ContentType.AUDIO);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_audio/upload")).thenReturn("plain".getBytes());
		when(bookStorage.contentType("items/item_audio/upload")).thenReturn("audio/mp4");
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getAssets().get(0).getMimeType()).isEqualTo("audio/mp4");
	}

	@Test
	void theStagedUploadIsDeletedOnceTheItemReachesReady() {
		CatalogueItem item = queued("item_clean", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_clean/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		verify(bookStorage).delete("items/item_clean/upload");
	}

	@Test
	void aStagingDeleteFailureDoesNotUndoAnAlreadySuccessfulIngest() {
		CatalogueItem item = queued("item_clean2", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_clean2/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));
		doThrow(new RuntimeException("bucket hiccup")).when(bookStorage).delete("items/item_clean2/upload");

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(item.getContentError()).isNull();
	}

	@Test
	void aFailureAfterContentIsWrittenCleansUpTheOrphanedObjects() {
		CatalogueItem item = queued("item_torn", AccessTier.OPEN_ACCESS, ContentType.EPUB);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_torn/upload")).thenReturn("plain".getBytes());
		// storeUnlocked writes the content object before search-index extraction runs, so a
		// throw here reproduces a real failure landing after bytes already exist in storage.
		when(searchIndexBuilder.build(any(), any(), any(), any()))
				.thenThrow(new IllegalStateException("failed to read EPUB archive"));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.FAILED);
		assertThat(item.getContentError()).contains("failed to read EPUB archive");
		verify(bookStorage).delete("items/item_torn/upload");
		verify(bookStorage).delete("items/item_torn/content");
		verify(bookStorage).delete("items/item_torn/index");
	}

	@Test
	void oneFailingItemDoesNotAbortTheBatch() {
		CatalogueItem bad = queued("item_bad", AccessTier.OPEN_ACCESS, ContentType.PDF);
		CatalogueItem good = queued("item_good", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(bad, good));
		when(bookStorage.load("items/item_bad/upload")).thenThrow(new RuntimeException("storage hiccup"));
		when(bookStorage.load("items/item_good/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(bad.getContentState()).isEqualTo(ContentState.FAILED);
		assertThat(bad.getContentError()).contains("storage hiccup");
		assertThat(good.getContentState()).isEqualTo(ContentState.READY);
	}

	@Test
	void anItemStuckPastTheTimeoutIsFailedWithAReasonNamingIt() {
		CatalogueItem stuck = queued("item_stuck", AccessTier.ELITE, ContentType.PDF);
		stuck.setContentState(ContentState.PROCESSING);
		stuck.setUpdatedAt(NOW.minus(Duration.ofMinutes(20)));
		when(items.findByContentStateInAndUpdatedAtBefore(eq(List.of(ContentState.QUEUED, ContentState.PROCESSING)),
				eq(NOW.minus(Duration.ofMinutes(15))))).thenReturn(List.of(stuck));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.expireStuck();

		assertThat(stuck.getContentState()).isEqualTo(ContentState.FAILED);
		assertThat(stuck.getContentError()).contains("15 minutes");
	}

	@Test
	void anItemWithinTheTimeoutIsUntouched() {
		when(items.findByContentStateInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

		processor.expireStuck();

		verify(items, never()).save(any());
	}

}
