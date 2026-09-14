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
import com.tf.reader.ingest.api.ObjectNotFoundException;
import com.tf.reader.ingest.index.BuiltSearchIndex;
import com.tf.reader.ingest.storage.StorageKeys;

/**
 * The background half of ingest. One {@code @Scheduled} loop drives every {@code QUEUED} part to
 * {@code READY} or {@code FAILED}, off the HTTP thread that queued it - there is no async/executor
 * infrastructure in this codebase, so, like {@code OfferSweeper} and {@code ExpirySweeper}, this
 * is a Mongo-driven poll. A second {@code @Scheduled} method is the watchdog: anything stuck in
 * {@code QUEUED} or {@code PROCESSING} past the timeout is failed with a reason naming it.
 *
 * <p>An item's own {@code contentState} is the aggregate of every part of every asset: {@code
 * FAILED} if any part failed, else {@code PROCESSING} if any part is processing, else {@code
 * QUEUED} if any part is queued, else {@code READY} only once every part is ready.
 * {@code catalogueVersion} bumps exactly once, on the transition into aggregate {@code READY} -
 * not per chapter, so an institution's feed cache is invalidated once per book, not once per
 * upload.
 *
 * <p>Every part is set to {@code PROCESSING} before any tier branching, whether or not it ends up
 * locked - open access is the one case that never gets a key, for any format, because handing a
 * key to an anonymous reader protects nothing. Audio is otherwise treated like any other format:
 * SUBSCRIPTION/ELITE audio is locked the same way a PDF or EPUB is, even though whole-file
 * encryption means the device must fully decrypt before it can play or seek - an accepted
 * tradeoff, not an oversight. A search index is built for every PDF/EPUB part regardless of tier -
 * audio never gets one, locked or not, since there's no text to extract - via the same
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
				processItem(item);
			}
			catch (RuntimeException e) {
				// A failure outside any one part's own try/catch below (e.g. iterating the item
				// itself) must not freeze every other item's queue - catch, fail the whole item,
				// carry on to the next one.
				log.error("ingest failed for item {}", item.getId(), e);
				failWholeItem(item, shortReason(e));
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
				String reason = "Ingest did not complete within " + watchdogTimeout.toMinutes() + " minutes";
				boolean anyStuckPart = false;
				for (CatalogueItem.Asset asset : item.getAssets()) {
					if (asset.getParts() == null) {
						continue;
					}
					for (CatalogueItem.Part part : asset.getParts()) {
						if (part.getContentState() == ContentState.QUEUED
								|| part.getContentState() == ContentState.PROCESSING) {
							failPart(item, asset, part, reason);
							anyStuckPart = true;
						}
					}
				}
				if (anyStuckPart) {
					recomputeAggregateAndSave(item);
				}
			}
			catch (RuntimeException e) {
				log.error("watchdog failed to expire item {}", item.getId(), e);
			}
		}
	}

	/**
	 * Every part of every asset still {@code QUEUED} on this item is processed in turn, on the
	 * same tick - a book with two queued chapters does not need two poll cycles. One part's
	 * failure fails only that part (surfacing the problem) without touching a sibling part's
	 * already-{@code READY} data; see {@link #failPart}.
	 */
	private void processItem(CatalogueItem item) {
		for (CatalogueItem.Asset asset : item.getAssets()) {
			if (asset.getParts() == null) {
				continue;
			}
			for (CatalogueItem.Part part : asset.getParts()) {
				if (part.getContentState() != ContentState.QUEUED) {
					continue;
				}
				try {
					processPart(item, asset, part);
				}
				catch (RuntimeException e) {
					// This part's own failure must not touch a sibling part's already-READY
					// data - fail only this part, clean up only what this part's attempt wrote,
					// and keep going.
					log.error("ingest failed for item {} part {}", item.getId(), part.getPartNumber(), e);
					failPart(item, asset, part, shortReason(e));
				}
			}
		}
		recomputeAggregateAndSave(item);
	}

	private void processPart(CatalogueItem item, CatalogueItem.Asset asset, CatalogueItem.Part part) {
		Instant queuedAt = part.getUpdatedAt();
		part.setContentState(ContentState.PROCESSING);
		part.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);

		String itemId = item.getId();
		ContentType format = asset.getFormat();
		int partNumber = part.getPartNumber();
		String stagingKey = StorageKeys.staging(itemId, partNumber);
		byte[] plaintext;
		String uploadedMimeType;
		try {
			plaintext = bookStorage.load(stagingKey);
			uploadedMimeType = bookStorage.contentType(stagingKey);
		}
		catch (ObjectNotFoundException e) {
			// The staged upload has not landed yet - a slow client upload, or a brief storage
			// blip, not a real failure. Go back to QUEUED for the next poll, keeping the
			// original queued timestamp rather than the one just set above, so the watchdog
			// still fails it if it is genuinely never coming, instead of retrying forever.
			part.setContentState(ContentState.QUEUED);
			part.setUpdatedAt(queuedAt);
			catalogueItemRepository.save(item);
			return;
		}

		if (TierRules.requiresLocking(item.getAccessTier(), format)) {
			storeLocked(itemId, asset, part, plaintext, uploadedMimeType);
		}
		else {
			storeUnlocked(itemId, asset, part, plaintext, uploadedMimeType);
		}

		part.setContentState(ContentState.READY);
		part.setContentError(null);
		part.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);

		deleteBestEffort(stagingKey, itemId);
	}

	/**
	 * Open access, any format: no key, no lock - but PDF/EPUB still gets a search index, built and
	 * stored plaintext, exactly like the content itself. A key handed to an anonymous reader
	 * protects nothing, so there is nothing to encrypt the index under either.
	 */
	private void storeUnlocked(String itemId, CatalogueItem.Asset asset, CatalogueItem.Part part, byte[] plaintext,
			String uploadedMimeType) {
		int partNumber = part.getPartNumber();
		String contentKey = StorageKeys.content(itemId, partNumber);
		String mimeType = AssetLocker.resolveMimeType(uploadedMimeType, asset.getFormat());
		bookStorage.store(contentKey, plaintext, mimeType);

		if (asset.getMimeType() == null) {
			asset.setMimeType(mimeType);
		}
		part.setSizeBytes(plaintext.length);
		part.setCipherLength(plaintext.length);

		String indexKey = null;
		Optional<BuiltSearchIndex> built = searchIndexBuilder.build(itemId, asset.getFormat(), plaintext, part);
		if (built.isPresent()) {
			indexKey = StorageKeys.index(itemId, partNumber);
			bookStorage.store(indexKey, built.get().json(), "application/json");
		}

		part.setStorageKey(contentKey);
		part.setIndexKey(indexKey);
	}

	private void storeLocked(String itemId, CatalogueItem.Asset asset, CatalogueItem.Part part, byte[] plaintext,
			String uploadedMimeType) {
		int partNumber = part.getPartNumber();
		AssetLocker.Result locked = assetLocker.lock(asset, partNumber, itemId, plaintext, uploadedMimeType);

		String contentKey = StorageKeys.content(itemId, partNumber);
		bookStorage.store(contentKey, locked.cipherContent(), asset.getMimeType());

		String indexKey = null;
		if (locked.cipherIndex() != null) {
			indexKey = StorageKeys.index(itemId, partNumber);
			bookStorage.store(indexKey, locked.cipherIndex(), "application/json");
		}

		asset.setMasterWrappedBek(locked.masterWrappedBek());
		part.setSizeBytes(locked.part().getSizeBytes());
		part.setCipherLength(locked.part().getCipherLength());
		part.setHasSearchIndex(locked.part().isHasSearchIndex());
		part.setIndexTerms(locked.part().getIndexTerms());
		part.setIndexSkipReason(locked.part().getIndexSkipReason());
		part.setStorageKey(contentKey);
		part.setIndexKey(indexKey);
	}

	/**
	 * FAILED if any part failed, else PROCESSING if any part is processing, else QUEUED if any
	 * part is queued, else READY only once every part of every asset is READY. The bump fires
	 * exactly once, on the transition into aggregate READY - a second chapter reaching READY on
	 * an already-READY item is a no-op here, not a second bump.
	 */
	private void recomputeAggregateAndSave(CatalogueItem item) {
		ContentState before = item.getContentState();
		List<CatalogueItem.Part> parts = allParts(item);
		ContentState after = aggregateOf(parts);
		item.setContentState(after);
		item.setContentError(after == ContentState.FAILED ? firstFailureReason(parts) : null);
		item.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);

		if (after == ContentState.READY && before != ContentState.READY) {
			catalogueVersionBumper.bump(CatalogueVersionBumper.Scope.ITEM, item.getId());
		}
	}

	private static List<CatalogueItem.Part> allParts(CatalogueItem item) {
		return item.getAssets().stream()
				.filter(asset -> asset.getParts() != null)
				.flatMap(asset -> asset.getParts().stream())
				.toList();
	}

	private static ContentState aggregateOf(List<CatalogueItem.Part> parts) {
		if (parts.stream().anyMatch(p -> p.getContentState() == ContentState.FAILED)) {
			return ContentState.FAILED;
		}
		if (parts.stream().anyMatch(p -> p.getContentState() == ContentState.PROCESSING)) {
			return ContentState.PROCESSING;
		}
		if (parts.stream().anyMatch(p -> p.getContentState() == ContentState.QUEUED)) {
			return ContentState.QUEUED;
		}
		return ContentState.READY;
	}

	private static String firstFailureReason(List<CatalogueItem.Part> parts) {
		return parts.stream().filter(p -> p.getContentState() == ContentState.FAILED)
				.map(CatalogueItem.Part::getContentError).filter(r -> r != null).findFirst().orElse(null);
	}

	/**
	 * The staged upload has already been consumed into the final content/index objects by this
	 * point - nothing reads it again. Deleted best-effort, after the part is durably READY: a
	 * cleanup failure here must not undo an ingest that already succeeded.
	 */
	private void deleteBestEffort(String key, String itemId) {
		try {
			bookStorage.delete(key);
		}
		catch (RuntimeException e) {
			log.warn("could not delete storage object {} for item {}", key, itemId, e);
		}
	}

	/**
	 * A failure can land after storeLocked/storeUnlocked already wrote content or index bytes for
	 * this part (e.g. search-index extraction throwing once the content object has landed).
	 * Deleting all three keys for the failing part is safe even when a given one was never
	 * written - delete is idempotent on a missing object. Only this part is marked FAILED; every
	 * sibling part's own already-READY data is untouched. The item's aggregate state is
	 * recomputed by the caller once every part in this poll pass has been attempted.
	 */
	private void failPart(CatalogueItem item, CatalogueItem.Asset asset, CatalogueItem.Part part, String reason) {
		part.setContentState(ContentState.FAILED);
		part.setContentError(reason);
		part.setUpdatedAt(clock.instant());
		catalogueItemRepository.save(item);

		String itemId = item.getId();
		ContentType format = asset.getFormat();
		int partNumber = part.getPartNumber();
		deleteBestEffort(StorageKeys.staging(itemId, partNumber), itemId);
		deleteBestEffort(StorageKeys.content(itemId, partNumber), itemId);
		deleteBestEffort(StorageKeys.index(itemId, partNumber), itemId);
	}

	/** Used only when a failure happens outside any one part's own attempt - see processQueued. */
	private void failWholeItem(CatalogueItem item, String reason) {
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
