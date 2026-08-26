package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.ingest.api.BookStorage;

/** The background half of ingest: queue draining and the watchdog. */
class IngestProcessorTest {

	private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final CatalogueItemRepository items = mock(CatalogueItemRepository.class);
	private final BookStorage bookStorage = mock(BookStorage.class);
	private final AssetLocker assetLocker = mock(AssetLocker.class);
	private final CatalogueVersionBumper bumper = mock(CatalogueVersionBumper.class);
	private final IngestProcessor processor = new IngestProcessor(items, bookStorage, assetLocker, bumper, CLOCK,
			Duration.ofMinutes(15));

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
	void openAccessPdfReachesReadyWithNoKeyAndNoIndexCall() {
		CatalogueItem item = queued("item_1", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_1/upload")).thenReturn("plain".getBytes());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
		assertThat(item.getMasterWrappedBek()).isNull();
		assertThat(item.getAssets().get(0).isEncrypted()).isFalse();
		verify(assetLocker, never()).lock(any(), any(), any());
		verify(bumper).bump(CatalogueVersionBumper.Scope.ITEM, "item_1");
	}

	@Test
	void subscriptionPdfIsLockedAndVersionBumped() {
		CatalogueItem item = queued("item_2", AccessTier.SUBSCRIPTION, ContentType.PDF);
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setEncrypted(true);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_2/upload")).thenReturn("plain".getBytes());
		when(assetLocker.lock("item_2", ContentType.PDF, "plain".getBytes()))
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
	void audioIsNeverLockedRegardlessOfTier() {
		CatalogueItem item = queued("item_3", AccessTier.ELITE, ContentType.AUDIO);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(item));
		when(bookStorage.load("items/item_3/upload")).thenReturn("plain".getBytes());
		when(items.save(any())).thenAnswer(i -> i.getArgument(0));

		processor.processQueued();

		verify(assetLocker, never()).lock(any(), any(), any());
		assertThat(item.getAssets().get(0).isEncrypted()).isFalse();
		assertThat(item.getContentState()).isEqualTo(ContentState.READY);
	}

	@Test
	void oneFailingItemDoesNotAbortTheBatch() {
		CatalogueItem bad = queued("item_bad", AccessTier.OPEN_ACCESS, ContentType.PDF);
		CatalogueItem good = queued("item_good", AccessTier.OPEN_ACCESS, ContentType.PDF);
		when(items.findByContentState(ContentState.QUEUED)).thenReturn(List.of(bad, good));
		when(bookStorage.load("items/item_bad/upload")).thenThrow(new RuntimeException("storage hiccup"));
		when(bookStorage.load("items/item_good/upload")).thenReturn("plain".getBytes());
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
