package com.tf.reader.ingest.service;

import java.util.Arrays;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.crypto.CryptoProperties;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.crypto.api.FileCipher;

import lombok.RequiredArgsConstructor;

/**
 * The encrypt-and-index sub-step for one locked part (SUBSCRIPTION/ELITE, any format including
 * audio). One BEK per asset, not per part: the first part of an asset ever locked generates and
 * wraps a fresh BEK; every later part of that same asset reuses the already-wrapped BEK (unwrap,
 * encrypt with a fresh nonce) - matching Readium LCP's model, one content key per publication.
 * Best-effort builds and encrypts a search index under the SAME BEK with its own new nonce. Audio
 * never has a text layer, so {@link SearchIndexBuilder} always skips it - the part still comes
 * back encrypted, just with a null {@code cipherIndex}.
 *
 * <p>Zeroes the BEK's own bytes before returning in every case, success or failure - the one
 * easy-to-skip, most-important step in this whole pipeline. A live BEK must not outlive this
 * call.
 */
@Component
@RequiredArgsConstructor
class AssetLocker {

	private final BookEncryptionKeys bookEncryptionKeys;
	private final FileCipher fileCipher;
	private final SearchIndexBuilder searchIndexBuilder;
	private final CryptoProperties cryptoProperties;

	record Result(CatalogueItem.Part part, byte[] cipherContent, byte[] cipherIndex, String masterWrappedBek) {
	}

	/**
	 * @param asset            the asset this part belongs to. If {@code asset.getMasterWrappedBek()}
	 *                         is already set, it is reused (unwrapped) rather than a new BEK being
	 *                         generated - every part of one asset shares exactly one key.
	 * @param uploadedMimeType the content type declared at upload time, if any - preferred over
	 *                         {@link #mimeTypeFor} since a real client-declared type (especially
	 *                         for audio, where "mpeg" is far from the only real format) beats a
	 *                         guess keyed only on {@link ContentType}
	 */
	Result lock(CatalogueItem.Asset asset, int partNumber, String itemId, byte[] plaintext, String uploadedMimeType) {
		String existingWrappedBek = asset.getMasterWrappedBek();
		SecretKey bek = existingWrappedBek == null ? bookEncryptionKeys.generate()
				: bookEncryptionKeys.unwrapWithMasterKey(existingWrappedBek);
		try {
			byte[] cipherContent = fileCipher.encrypt(bek, plaintext);

			CatalogueItem.Part part = new CatalogueItem.Part();
			part.setPartNumber(partNumber);
			part.setSizeBytes(plaintext.length);
			part.setCipherLength(cipherContent.length);

			byte[] cipherIndex = searchIndexBuilder.build(itemId, asset.getFormat(), plaintext, part)
					.map(built -> fileCipher.encrypt(bek, built.json()))
					.orElse(null);

			String mimeType = resolveMimeType(uploadedMimeType, asset.getFormat());
			if (asset.getMimeType() == null) {
				asset.setMimeType(mimeType);
			}
			asset.setEncrypted(true);
			asset.setKeyId(cryptoProperties.masterKeyId());

			String masterWrappedBek = existingWrappedBek != null ? existingWrappedBek
					: bookEncryptionKeys.wrapWithMasterKey(bek);
			return new Result(part, cipherContent, cipherIndex, masterWrappedBek);
		}
		finally {
			Arrays.fill(bek.getEncoded(), (byte) 0);
		}
	}

	static String resolveMimeType(String uploadedMimeType, ContentType contentType) {
		return (uploadedMimeType != null && !uploadedMimeType.isBlank()) ? uploadedMimeType : mimeTypeFor(contentType);
	}

	private static String mimeTypeFor(ContentType type) {
		return switch (type) {
			case PDF -> "application/pdf";
			case EPUB -> "application/epub+zip";
			case AUDIO -> "audio/mpeg";
		};
	}

}
