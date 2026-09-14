package com.tf.reader.content.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.content.api.LoanProof;
import com.tf.reader.content.api.ContentAccessGrant;
import com.tf.reader.content.api.ContentGrant;
import com.tf.reader.content.api.ContentGrantRequest;
import com.tf.reader.content.api.Encryption;
import com.tf.reader.content.api.IndexUrl;
import com.tf.reader.content.api.SignedUrl;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.ingest.api.BookStorage;
import com.tf.reader.ingest.api.PresignedObject;

import lombok.RequiredArgsConstructor;

/**
 * Real signed URLs and real per-book keys, backed by whatever {@code IngestProcessor} actually
 * wrote: {@code part.storageKey}/{@code part.indexKey} for the bytes in object storage,
 * {@code asset.masterWrappedBek} for the key, shared by every part of that asset. No fixtures, no
 * fake BEK - the mock this replaced is retired now that a book can genuinely reach {@code READY}
 * through the real ingest pipeline. {@code request.partNumber()} defaults to 1, so a
 * non-chaptered item (exactly one part) behaves exactly as before.
 *
 * <p>Device-key validation and the RSA-OAEP-256/MGF1-SHA-256 wrap (B17) both live in
 * {@link BookEncryptionKeys} already - this class never touches raw key material or JCE ciphers
 * itself, only the already-tested seam.
 */
@Service
@RequiredArgsConstructor
class ContentAccessGrantImpl implements ContentAccessGrant {

	private static final Duration URL_TTL = Duration.ofMinutes(15);

	private final CatalogueItemRepository catalogueItemRepository;
	private final BookStorage bookStorage;
	private final BookEncryptionKeys bookEncryptionKeys;
	private final EntitlementQuery entitlementQuery;

	@Override
	public ContentGrant grant(ContentGrantRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		if (request.itemId() == null || request.itemId().isBlank()) {
			throw new IllegalArgumentException("itemId is required");
		}
		if (request.subject() == null) {
			throw new IllegalArgumentException("subject is required");
		}

		CatalogueItem item = catalogueItemRepository.findById(request.itemId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such catalogue item"));
		if (item.getContentState() != ContentState.READY) {
			throw new ApiException(ErrorCode.CONTENT_NOT_READY, "This book is not ready to be read yet.");
		}

		// Never trusted implicitly, even though the one caller today already checks both before
		// calling grant() - a second caller of this public seam must not be able to skip either.
		// Runs after the item lookup above so a genuinely missing/not-ready item still gets its
		// own specific message, rather than the generic "not entitled" this check would otherwise
		// produce for the same two DenyReasons.
		EntitlementDecision decision = entitlementQuery.check(request.subject(), request.itemId());
		if (!decision.entitled()) {
			throw new ApiException(mapDenyReason(decision.reason()), "Not entitled to this content.");
		}
		requireActiveLoan(request.loanProof());

		CatalogueItem.Asset asset = assetFor(item, request);
		int partNumber = request.partNumber() == null ? 1 : request.partNumber();
		CatalogueItem.Part part = partFor(asset, partNumber);
		if (asset.isEncrypted() && request.devicePublicKey() == null) {
			throw new ApiException(ErrorCode.INVALID_DEVICE_PUBLIC_KEY,
					"devicePublicKey is required for encrypted content");
		}

		PresignedObject contentUrl = bookStorage.presign(part.getStorageKey(), URL_TTL);
		SignedUrl content = new SignedUrl(contentUrl.url(), contentUrl.expiresAt(), part.getCipherLength(),
				part.getSizeBytes(), asset.getMimeType());

		IndexUrl index = null;
		if (request.wantSearchIndex() && part.getIndexKey() != null) {
			PresignedObject indexUrl = bookStorage.presign(part.getIndexKey(), URL_TTL);
			index = new IndexUrl(indexUrl.url(), asset.isEncrypted(), part.getIndexTerms());
		}

		Encryption encryption = null;
		if (asset.isEncrypted()) {
			String wrappedBek = bookEncryptionKeys.rewrapForDevice(asset.getMasterWrappedBek(),
					request.devicePublicKey());
			encryption = new Encryption("AES-256-GCM", "nonce(12) || ciphertext || tag(16)", wrappedBek,
					"RSA-OAEP-256", asset.getKeyId(), fingerprintOf(request.devicePublicKey()));
		}

		return new ContentGrant(content, index, encryption);
	}

	/**
	 * Today's ingest pipeline writes exactly one asset per item, but {@code assets} is a list (a
	 * multi-format item is a real, if unused, shape), so this matches by format rather than
	 * assuming index 0.
	 */
	// A null dueAt is a valid, non-expiring loan (ELITE, per LoanProof's own convention) - only a
	// dueAt already in the past is rejected.
	private static void requireActiveLoan(LoanProof loanProof) {
		if (loanProof == null || (loanProof.dueAt() != null && loanProof.dueAt().isBefore(Instant.now()))) {
			throw new ApiException(ErrorCode.NO_ACTIVE_LOAN, "No active loan backs this request.");
		}
	}

	/**
	 * A part that is not itself READY is CONTENT_NOT_READY even when the item's own aggregate
	 * state is READY - a sibling chapter can be ready while this one is still processing.
	 */
	private static CatalogueItem.Part partFor(CatalogueItem.Asset asset, int partNumber) {
		List<CatalogueItem.Part> parts = asset.getParts();
		if (parts != null) {
			for (CatalogueItem.Part part : parts) {
				if (part.getPartNumber() == partNumber && part.getContentState() == ContentState.READY) {
					return part;
				}
			}
		}
		throw new ApiException(ErrorCode.CONTENT_NOT_READY, "This chapter is not ready to be read yet.");
	}

	private static ErrorCode mapDenyReason(DenyReason reason) {
		if (reason == null) {
			return ErrorCode.NO_ENTITLEMENT;
		}
		return switch (reason) {
			case NO_ENTITLEMENT -> ErrorCode.NO_ENTITLEMENT;
			case ENTITLEMENT_EXPIRED -> ErrorCode.ENTITLEMENT_EXPIRED;
			case ENTITLEMENT_SUSPENDED -> ErrorCode.ENTITLEMENT_SUSPENDED;
			case INSTITUTION_INACTIVE -> ErrorCode.INSTITUTION_INACTIVE;
			case CONTENT_NOT_READY -> ErrorCode.CONTENT_NOT_READY;
			case NOT_FOUND -> ErrorCode.NOT_FOUND;
		};
	}

	private static CatalogueItem.Asset assetFor(CatalogueItem item, ContentGrantRequest request) {
		List<CatalogueItem.Asset> assets = item.getAssets();
		if (assets != null) {
			for (CatalogueItem.Asset asset : assets) {
				if (asset.getFormat().name().equals(request.format().name())) {
					return asset;
				}
			}
		}
		throw new ApiException(ErrorCode.CONTENT_NOT_READY, "No ready asset for the requested format.");
	}

	private static String fingerprintOf(byte[] devicePublicKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(devicePublicKey);
			return "sha256:" + HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

}
