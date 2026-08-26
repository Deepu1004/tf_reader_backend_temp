package com.tf.reader.ingest.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.storage.StorageKeys;

/**
 * The background half of ingest. One {@code @Scheduled} loop drives every {@code QUEUED} item to
 * {@code READY} or {@code FAILED}, off the HTTP thread that queued it - there is no async/executor
 * infrastructure in this codebase, so, like {@code OfferSweeper} and {@code ExpirySweeper}, this
 * is a Mongo-driven poll. A second {@code @Scheduled} method is the watchdog: anything stuck in
 * {@code QUEUED} or {@code PROCESSING} past the timeout is failed with a reason naming it.
 *
 * <p>Every {@code QUEUED} item is set to {@code PROCESSING} before any tier branching - the
 * contract's own {@code ContentState} description says open access and audio "pass through it
 * without ever being encrypted," not that they skip it, so they get the same momentary state
 * transition as a locked asset; only the encryption/indexing sub-step is skipped for them.
 */
@Service
public class IngestProcessor {

	private static final Logger log = LoggerFactory.getLogger(IngestProcessor.class);

	private final CatalogueItemRepository catalogueItemRepository;
	private final BookStorage bookStorage;
	private final AssetLocker assetLocker;
	private final CatalogueVersionBumper catalogueVersionBumper;
	private final Clock clock;
	private final Duration watchdogTimeout;

	public IngestProcessor(CatalogueItemRepository catalogueItemRepository, BookStorage bookStorage,
			AssetLocker assetLocker, CatalogueVersionBumper catalogueVersionBumper, Clock clock,
			@Value("${tf.ingest.watchdog-timeout:15m}") Duration watchdogTimeout) {
		this.catalogueItemRepository = catalogueItemRepository;
		this.bookStorage = bookStorage;
		this.assetLocker = assetLocker;
		this.catalogueVersionBumper = catalogueVersionBumper;
		this.clock = clock;
		this.watchdogTimeout = watchdogTimeout;
	}

	@Scheduled(fixedDelayString = "${tf.ingest.poll-interval:5s}")
	public void processQueued() {
		for (CatalogueItem item : catalogueItemRepository.findByContentState(ContentState.QUEUED)) {
			try {
				processOne(item);
			}
			catch (RuntimeException e) {
				// One bad upload must not freeze every other item's queue - catch, fail it, carry on.
				log.error("ingest failed for item {}", item.getId(), e);
				fail(item, shortReason(e));
			}
		}
	}

	@Scheduled(fixedDelayString = "${tf.ingest.watchdog-interval:1m}")
	public void expireStuck() {
		Instant cutoff = clock.instant().minus(watchdogTimeout);
		List<CatalogueItem> stuck = catalogueItemRepository
				.findByContentStateInAndUpdatedAtBefore(List.of(ContentState.QUEUED, ContentState.PROCESSING), cutoff);
		for (CatalogueItem item : stuck) {
			try {
				fail(item, "Ingest did not complete within " + watchdogTimeout.toMinutes() + " minutes");
			}
			catch (RuntimeException e) {
				log.error("watchdog failed to expire item {}", item.getId(), e);
			}
		}
	}

	private void processOne(CatalogueItem item) {
		item.setContentState(ContentState.PROCESSING);
		item.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);

		String itemId = item.getId();
		ContentType contentType = item.getContentType();
		byte[] plaintext = bookStorage.load(StorageKeys.staging(itemId));

		if (TierRules.requiresLocking(item.getAccessTier(), contentType)) {
			storeLocked(item, contentType, plaintext);
		}
		else {
			storeUnlocked(item, contentType, plaintext);
		}

		item.setContentState(ContentState.READY);
		item.setContentError(null);
		item.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);
		catalogueVersionBumper.bump(CatalogueVersionBumper.Scope.ITEM, itemId);
	}

	private void storeUnlocked(CatalogueItem item, ContentType contentType, byte[] plaintext) {
		String contentKey = StorageKeys.content(item.getId());
		String mimeType = mimeTypeFor(contentType);
		bookStorage.store(contentKey, plaintext, mimeType);

		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(contentType);
		asset.setMimeType(mimeType);
		asset.setSizeBytes(plaintext.length);
		asset.setCipherLength(plaintext.length);
		asset.setEncrypted(false);

		item.setStorageKey(contentKey);
		item.setIndexKey(null);
		item.setAssets(List.of(asset));
	}

	private void storeLocked(CatalogueItem item, ContentType contentType, byte[] plaintext) {
		AssetLocker.Result locked = assetLocker.lock(item.getId(), contentType, plaintext);

		String contentKey = StorageKeys.content(item.getId());
		bookStorage.store(contentKey, locked.cipherContent(), locked.asset().getMimeType());

		String indexKey = null;
		if (locked.cipherIndex() != null) {
			indexKey = StorageKeys.index(item.getId());
			bookStorage.store(indexKey, locked.cipherIndex(), "application/json");
		}

		item.setMasterWrappedBek(locked.masterWrappedBek());
		item.setStorageKey(contentKey);
		item.setIndexKey(indexKey);
		item.setAssets(List.of(locked.asset()));
	}

	private void fail(CatalogueItem item, String reason) {
		item.setContentState(ContentState.FAILED);
		item.setContentError(reason);
		item.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);
	}

	private static String shortReason(RuntimeException e) {
		String message = e.getMessage();
		return message == null ? e.getClass().getSimpleName() : message;
	}

	private static String mimeTypeFor(ContentType type) {
		return switch (type) {
			case PDF -> "application/pdf";
			case EPUB -> "application/epub+zip";
			case AUDIO -> "audio/mpeg";
		};
	}

}
