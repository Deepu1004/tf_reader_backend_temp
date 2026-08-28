package com.tf.reader.ingest.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
import com.tf.reader.ingest.index.BuiltSearchIndex;
import com.tf.reader.ingest.storage.StorageKeys;

/**
 * The background half of ingest. One {@code @Scheduled} loop drives every {@code QUEUED} item to
 * {@code READY} or {@code FAILED}, off the HTTP thread that queued it - there is no async/executor
 * infrastructure in this codebase, so, like {@code OfferSweeper} and {@code ExpirySweeper}, this
 * is a Mongo-driven poll. A second {@code @Scheduled} method is the watchdog: anything stuck in
 * {@code QUEUED} or {@code PROCESSING} past the timeout is failed with a reason naming it.
 *
 * <p>Every {@code QUEUED} item is set to {@code PROCESSING} before any tier branching, whether or
 * not it ends up locked - open access is the one case that never gets a key, for any format,
 * because handing a key to an anonymous reader protects nothing. Audio is otherwise treated like
 * any other format: SUBSCRIPTION/ELITE audio is now locked the same way a PDF or EPUB is, even
 * though whole-file encryption means the device must fully decrypt before it can play or seek -
 * an accepted tradeoff, not an oversight. A search index is built for every PDF/EPUB regardless of
 * tier - audio never gets one, locked or not, since there's no text to extract - via the same
 * {@link SearchIndexBuilder} both branches share.
 */
@Service
public class IngestProcessor {

	private static final Logger log = LoggerFactory.getLogger(IngestProcessor.class);

	private final CatalogueItemRepository catalogueItemRepository;
	private final BookStorage bookStorage;
	private final AssetLocker assetLocker;
	private final SearchIndexBuilder searchIndexBuilder;
	private final CatalogueVersionBumper catalogueVersionBumper;
	private final Clock clock;
	private final Duration watchdogTimeout;

	public IngestProcessor(CatalogueItemRepository catalogueItemRepository, BookStorage bookStorage,
			AssetLocker assetLocker, SearchIndexBuilder searchIndexBuilder,
			CatalogueVersionBumper catalogueVersionBumper, Clock clock,
			@Value("${tf.ingest.watchdog-timeout:15m}") Duration watchdogTimeout) {
		this.catalogueItemRepository = catalogueItemRepository;
		this.bookStorage = bookStorage;
		this.assetLocker = assetLocker;
		this.searchIndexBuilder = searchIndexBuilder;
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
		String stagingKey = StorageKeys.staging(itemId);
		byte[] plaintext = bookStorage.load(stagingKey);
		String uploadedMimeType = bookStorage.contentType(stagingKey);

		if (TierRules.requiresLocking(item.getAccessTier(), contentType)) {
			storeLocked(item, contentType, plaintext, uploadedMimeType);
		}
		else {
			storeUnlocked(item, contentType, plaintext, uploadedMimeType);
		}

		item.setContentState(ContentState.READY);
		item.setContentError(null);
		item.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);
		catalogueVersionBumper.bump(CatalogueVersionBumper.Scope.ITEM, itemId);

		deleteStagingBestEffort(stagingKey, itemId);
	}

	/**
	 * The staged upload has already been consumed into the final content/index objects by this
	 * point - nothing reads it again. Deleted best-effort, after the item is durably READY: a
	 * cleanup failure here must not undo an ingest that already succeeded.
	 */
	private void deleteStagingBestEffort(String stagingKey, String itemId) {
		try {
			bookStorage.delete(stagingKey);
		}
		catch (RuntimeException e) {
			log.warn("could not delete staging object for item {}", itemId, e);
		}
	}

	/**
	 * Open access, any format: no key, no lock - but PDF/EPUB still gets a search index, built and
	 * stored plaintext, exactly like the content itself. A key handed to an anonymous reader
	 * protects nothing, so there is nothing to encrypt the index under either.
	 */
	private void storeUnlocked(CatalogueItem item, ContentType contentType, byte[] plaintext, String uploadedMimeType) {
		String contentKey = StorageKeys.content(item.getId());
		String mimeType = AssetLocker.resolveMimeType(uploadedMimeType, contentType);
		bookStorage.store(contentKey, plaintext, mimeType);

		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(contentType);
		asset.setMimeType(mimeType);
		asset.setSizeBytes(plaintext.length);
		asset.setCipherLength(plaintext.length);
		asset.setEncrypted(false);

		String indexKey = null;
		Optional<BuiltSearchIndex> built = searchIndexBuilder.build(item.getId(), contentType, plaintext, asset);
		if (built.isPresent()) {
			indexKey = StorageKeys.index(item.getId());
			bookStorage.store(indexKey, built.get().json(), "application/json");
		}

		item.setStorageKey(contentKey);
		item.setIndexKey(indexKey);
		item.setAssets(List.of(asset));
	}

	private void storeLocked(CatalogueItem item, ContentType contentType, byte[] plaintext, String uploadedMimeType) {
		AssetLocker.Result locked = assetLocker.lock(item.getId(), contentType, plaintext, uploadedMimeType);

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

}
