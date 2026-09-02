package com.tf.reader.content.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
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
 * wrote: {@code item.storageKey}/{@code item.indexKey} for the bytes in object storage,
 * {@code masterWrappedBek} for the key. No fixtures, no fake BEK - the mock this replaced is
 * retired now that a book can genuinely reach {@code READY} through the real ingest pipeline.
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

	@Override
	public ContentGrant grant(ContentGrantRequest request) {
		if (request == null) {
			throw new IllegalArgumentException("request is required");
		}
		if (request.itemId() == null || request.itemId().isBlank()) {
			throw new IllegalArgumentException("itemId is required");
		}

		CatalogueItem item = catalogueItemRepository.findById(request.itemId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such catalogue item"));
		if (item.getContentState() != ContentState.READY) {
			throw new ApiException(ErrorCode.CONTENT_NOT_READY, "This book is not ready to be read yet.");
		}

		CatalogueItem.Asset asset = assetFor(item, request);
		if (asset.isEncrypted() && request.devicePublicKey() == null) {
			throw new ApiException(ErrorCode.INVALID_DEVICE_PUBLIC_KEY,
					"devicePublicKey is required for encrypted content");
		}

		PresignedObject contentUrl = bookStorage.presign(item.getStorageKey(), URL_TTL);
		SignedUrl content = new SignedUrl(contentUrl.url(), contentUrl.expiresAt(), asset.getCipherLength(),
				asset.getSizeBytes(), asset.getMimeType());

		IndexUrl index = null;
		if (request.wantSearchIndex() && item.getIndexKey() != null) {
			PresignedObject indexUrl = bookStorage.presign(item.getIndexKey(), URL_TTL);
			index = new IndexUrl(indexUrl.url(), asset.isEncrypted(), asset.getIndexTerms());
		}

		Encryption encryption = null;
		if (asset.isEncrypted()) {
			String wrappedBek = bookEncryptionKeys.rewrapForDevice(item.getMasterWrappedBek(),
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
