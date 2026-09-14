package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.ArrayList;
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

/** The background half of ingest: queue draining and the watchdog, now per-part. */
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

	private static CatalogueItem.Part queuedPart(int partNumber) {
		CatalogueItem.Part part = new CatalogueItem.Part();
		part.setPartNumber(partNumber);
		part.setContentState(ContentState.QUEUED);
		part.setUpdatedAt(NOW);
		return part;
	}

	private static CatalogueItem itemWithOnePart(String id, AccessTier tier, ContentType type) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setAccessTier(tier);
		item.setContentType(type);
		item.setContentState(ContentState.QUEUED);
		item.setUpdatedAt(NOW);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(type);
		asset.setParts(new ArrayList<>(List.of(queuedPart(1))));
		item.setAssets(new ArrayList<>(List.of(asset)));
		return item;
	}

	private static CatalogueItem.Asset assetOf(CatalogueItem item) {
		return item.getAssets().get(0);
	}

	private static CatalogueItem.Part partOf(CatalogueItem item, int partNumber) {
		return assetOf(item).getParts().stream().filter(p -> p.getPartNumber() == partNumber).findFirst()
				.orElseThrow();
	}

	@Test
	void openAccessPdfReachesReadyWithNoKeyButStillGetsAPlaintextIndex() {
		CatalogueItem item = itemWithOnePart("item_1", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_1/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(eq("item_1"), eq(ContentType.PDF), any(), any())).thenAnswer(invocation -> {
			CatalogueItem.Part part = invocation.getArgument(3);
			part.setHasSearchIndex(true);
			part.setIndexTerms(10);
			return Optional.of(new BuiltSearchIndex("index-json".getBytes(), 10));
		});
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(assetOf(item).getMasterWrappedBek()).isNull();
		assertThat(assetOf(item).isEncrypted()).isFalse();
		assertThat(partOf(item, 1).getIndexKey()).isEqualTo("items/item_1/index");
		verify(bookStorage).store("items/item_1/index", "index-json".getBytes(), "application/json");
		verify(assetLocker, never()).lock(any(), anyInt(), any(), any(), any());
		verify(bumper).bump(CatalogueVersionBumper.Scope.ITEM, "item_1");
	}

	@Test
	void openAccessPdfWithNoTextLayerReachesReadyWithNoIndexAtAll() {
		CatalogueItem item = itemWithOnePart("item_1b", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_1b/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(partOf(item, 1).getIndexKey()).isNull();
		verify(bookStorage, never()).store(eq("items/item_1b/index"), any(), any());
	}

	@Test
	void subscriptionPdfIsLockedAndVersionBumped() {
		CatalogueItem item = itemWithOnePart("item_2", AccessTier.SUBSCRIPTION, ContentType.PDF);
		CatalogueItem.Part lockedPart = new CatalogueItem.Part();
		lockedPart.setPartNumber(1);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_2/upload")).thenReturn("plain".getBytes());
		when(assetLocker.lock(any(), eq(1), eq("item_2"), any(), any()))
				.thenReturn(new AssetLocker.Result(lockedPart, "cipher".getBytes(), null, "wrapped-bek"));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(assetOf(item).getMasterWrappedBek()).isEqualTo("wrapped-bek");
		assertThat(partOf(item, 1).getStorageKey()).isEqualTo("items/item_2/content");
		verify(bookStorage).store("items/item_2/content", "cipher".getBytes(), assetOf(item).getMimeType());
		verify(bumper).bump(CatalogueVersionBumper.Scope.ITEM, "item_2");
	}

	@Test
	void openAccessAudioIsNeverLockedAndNeverIndexed() {
		CatalogueItem item = itemWithOnePart("item_3", AccessTier.OPEN_ACCESS, ContentType.AUDIO);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_3/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		verify(assetLocker, never()).lock(any(), anyInt(), any(), any(), any());
		assertThat(assetOf(item).isEncrypted()).isFalse();
		assertThat(partOf(item, 1).getIndexKey()).isNull();
		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
	}

	@Test
	void subscriptionAndEliteAudioIsNowLockedLikeAnyOtherFormatButStillNeverIndexed() {
		CatalogueItem item = itemWithOnePart("item_3b", AccessTier.ELITE, ContentType.AUDIO);
		CatalogueItem.Part lockedPart = new CatalogueItem.Part();
		lockedPart.setPartNumber(1);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_3b/upload")).thenReturn("plain".getBytes());
		when(assetLocker.lock(any(), eq(1), eq("item_3b"), any(), any())).thenAnswer(invocation -> {
			// The real AssetLocker mutates the asset it's handed (encrypted/keyId/mimeType) as a
			// side effect - mimicked here since this call is mocked, not the real method body.
			CatalogueItem.Asset asset = invocation.getArgument(0);
			asset.setEncrypted(true);
			return new AssetLocker.Result(lockedPart, "cipher".getBytes(), null, "wrapped-bek");
		});
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(assetOf(item).getMasterWrappedBek()).isEqualTo("wrapped-bek");
		assertThat(assetOf(item).isEncrypted()).isTrue();
		assertThat(partOf(item, 1).getIndexKey()).isNull();
		verify(bookStorage).store("items/item_3b/content", "cipher".getBytes(), assetOf(item).getMimeType());
	}

	@Test
	void audioUsesTheRealUploadedMimeTypeNotAHardcodedGuess() {
		CatalogueItem item = itemWithOnePart("item_audio", AccessTier.OPEN_ACCESS, ContentType.AUDIO);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_audio/upload")).thenReturn("plain".getBytes());
		when(bookStorage.contentType("items/item_audio/upload")).thenReturn("audio/mp4");
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(assetOf(item).getMimeType()).isEqualTo("audio/mp4");
	}

	@Test
	void theStagedUploadIsDeletedOnceThePartReachesReady() {
		CatalogueItem item = itemWithOnePart("item_clean", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_clean/upload")).thenReturn("plain".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		verify(bookStorage).delete("items/item_clean/upload");
	}

	@Test
	void aStagingDeleteFailureDoesNotUndoAnAlreadySuccessfulIngest() {
		CatalogueItem item = itemWithOnePart("item_clean2", AccessTier.OPEN_ACCESS, ContentType.PDF);
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
	void aFailureAfterContentIsWrittenCleansUpTheOrphanedObjectsForThatPartOnly() {
		CatalogueItem item = itemWithOnePart("item_torn", AccessTier.OPEN_ACCESS, ContentType.EPUB);
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
		assertThat(partOf(item, 1).getContentState()).isEqualTo(ContentState.FAILED);
		verify(bookStorage).delete("items/item_torn/upload");
		verify(bookStorage).delete("items/item_torn/content");
		verify(bookStorage).delete("items/item_torn/index");
	}

	@Test
	void oneFailingItemDoesNotAbortTheBatch() {
		CatalogueItem bad = itemWithOnePart("item_bad", AccessTier.OPEN_ACCESS, ContentType.PDF);
		CatalogueItem good = itemWithOnePart("item_good", AccessTier.OPEN_ACCESS, ContentType.PDF);
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

	/**
	 * Two chapters of one locked asset, both queued in the same poll pass: both reach READY,
	 * with distinct keys, sharing one asset-level BEK, and the bump fires exactly once - not
	 * once per chapter.
	 */
	@Test
	void twoQueuedPartsOfOneLockedAssetBothReachReadyWithASharedBekAndOneBump() {
		CatalogueItem item = new CatalogueItem();
		item.setId("item_multi");
		item.setAccessTier(AccessTier.SUBSCRIPTION);
		item.setContentType(ContentType.PDF);
		item.setContentState(ContentState.QUEUED);
		item.setUpdatedAt(NOW);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setParts(new ArrayList<>(List.of(queuedPart(1), queuedPart(2))));
		item.setAssets(new ArrayList<>(List.of(asset)));

		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_multi/upload")).thenReturn("chapter-1".getBytes());
		when(bookStorage.load("items/item_multi/part2/upload")).thenReturn("chapter-2".getBytes());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		when(assetLocker.lock(any(), eq(1), eq("item_multi"), any(), any())).thenAnswer(invocation -> {
			CatalogueItem.Part part = new CatalogueItem.Part();
			part.setPartNumber(1);
			return new AssetLocker.Result(part, "cipher-1".getBytes(), null, "shared-wrapped-bek");
		});
		when(assetLocker.lock(any(), eq(2), eq("item_multi"), any(), any())).thenAnswer(invocation -> {
			CatalogueItem.Part part = new CatalogueItem.Part();
			part.setPartNumber(2);
			return new AssetLocker.Result(part, "cipher-2".getBytes(), null, "shared-wrapped-bek");
		});

		processor.processQueued();

		assertThat(partOf(item, 1).getContentState()).isEqualTo(ContentState.READY);
		assertThat(partOf(item, 2).getContentState()).isEqualTo(ContentState.READY);
		assertThat(partOf(item, 1).getStorageKey()).isEqualTo("items/item_multi/content");
		assertThat(partOf(item, 2).getStorageKey()).isEqualTo("items/item_multi/part2/content");
		assertThat(assetOf(item).getMasterWrappedBek()).isEqualTo("shared-wrapped-bek");
		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		verify(bumper, org.mockito.Mockito.times(1)).bump(CatalogueVersionBumper.Scope.ITEM, "item_multi");
	}

	/**
	 * Chapter 2's staged object fails to load; chapter 1 already succeeded. The item's
	 * aggregate goes FAILED (surfacing the problem), but chapter 1's own READY data is left
	 * untouched - a sibling part's failure never rolls back what already succeeded.
	 */
	@Test
	void onePartFailingLeavesASiblingsAlreadyReadyDataIntact() {
		CatalogueItem item = new CatalogueItem();
		item.setId("item_partial");
		item.setAccessTier(AccessTier.OPEN_ACCESS);
		item.setContentType(ContentType.PDF);
		item.setContentState(ContentState.QUEUED);
		item.setUpdatedAt(NOW);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		CatalogueItem.Part part1 = new CatalogueItem.Part();
		part1.setPartNumber(1);
		part1.setContentState(ContentState.READY);
		part1.setStorageKey("items/item_partial/content");
		asset.setParts(new ArrayList<>(List.of(part1, queuedPart(2))));
		item.setAssets(new ArrayList<>(List.of(asset)));

		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_partial/part2/upload"))
				.thenThrow(new RuntimeException("storage hiccup on chapter 2"));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.FAILED);
		assertThat(partOf(item, 1).getContentState()).isEqualTo(ContentState.READY);
		assertThat(partOf(item, 1).getStorageKey()).isEqualTo("items/item_partial/content");
		assertThat(partOf(item, 2).getContentState()).isEqualTo(ContentState.FAILED);
	}

	@Test
	void anItemStuckPastTheTimeoutIsFailedWithAReasonNamingIt() {
		CatalogueItem stuck = itemWithOnePart("item_stuck", AccessTier.ELITE, ContentType.PDF);
		partOf(stuck, 1).setContentState(ContentState.PROCESSING);
		stuck.setContentState(ContentState.PROCESSING);
		stuck.setUpdatedAt(NOW.minus(Duration.ofMinutes(20)));
		when(items.findByContentStateInAndUpdatedAtBefore(eq(List.of(ContentState.QUEUED, ContentState.PROCESSING)),
				eq(NOW.minus(Duration.ofMinutes(15))))).thenReturn(List.of(stuck));
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.expireStuck();

		assertThat(stuck.getContentState()).isEqualTo(ContentState.FAILED);
		assertThat(stuck.getContentError()).contains("15 minutes");
		assertThat(partOf(stuck, 1).getContentState()).isEqualTo(ContentState.FAILED);
	}

	@Test
	void anItemWithinTheTimeoutIsUntouched() {
		when(items.findByContentStateInAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

		processor.expireStuck();

		verify(items, never()).save(any());
	}

}
