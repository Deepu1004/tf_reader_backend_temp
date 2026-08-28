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
 * The encrypt-and-index sub-step for a locked asset (SUBSCRIPTION/ELITE, any format including
 * audio): generate a BEK, encrypt the plaintext under it, best-effort build and encrypt a search
 * index under the SAME BEK with a new nonce, then wrap the BEK under the master key. Audio never
 * has a text layer, so {@link SearchIndexBuilder} always skips it - the asset still comes back
 * encrypted, just with a null {@code cipherIndex}.
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

	record Result(CatalogueItem.Asset asset, byte[] cipherContent, byte[] cipherIndex, String masterWrappedBek) {
	}

	/**
	 * @param uploadedMimeType the content type declared at upload time, if any - preferred over
	 *                         {@link #mimeTypeFor} since a real client-declared type (especially
	 *                         for audio, where "mpeg" is far from the only real format) beats a
	 *                         guess keyed only on {@link ContentType}
	 */
	Result lock(String itemId, ContentType contentType, byte[] plaintext, String uploadedMimeType) {
		SecretKey bek = bookEncryptionKeys.generate();
		try {
			byte[] cipherContent = fileCipher.encrypt(bek, plaintext);

			CatalogueItem.Asset asset = new CatalogueItem.Asset();
			asset.setFormat(contentType);
			asset.setMimeType(resolveMimeType(uploadedMimeType, contentType));
			asset.setSizeBytes(plaintext.length);
			asset.setEncrypted(true);
			asset.setCipherLength(cipherContent.length);
			asset.setKeyId(cryptoProperties.masterKeyId());

			byte[] cipherIndex = searchIndexBuilder.build(itemId, contentType, plaintext, asset)
					.map(built -> fileCipher.encrypt(bek, built.json()))
					.orElse(null);

			String masterWrappedBek = bookEncryptionKeys.wrapWithMasterKey(bek);
			return new Result(asset, cipherContent, cipherIndex, masterWrappedBek);
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
