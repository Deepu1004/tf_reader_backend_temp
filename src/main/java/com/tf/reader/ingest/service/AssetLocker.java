package com.tf.reader.ingest.service;

import java.util.Arrays;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.crypto.CryptoProperties;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.crypto.api.FileCipher;
import com.tf.reader.ingest.index.BuiltSearchIndex;
import com.tf.reader.ingest.index.NoTextLayerException;
import com.tf.reader.ingest.index.SearchIndexService;

import lombok.RequiredArgsConstructor;

/**
 * The encrypt-and-index sub-step for a locked asset (SUBSCRIPTION/ELITE, non-audio): generate a
 * BEK, encrypt the plaintext under it, best-effort build and encrypt a search index under the
 * SAME BEK with a new nonce, then wrap the BEK under the master key.
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
	private final SearchIndexService searchIndexService;
	private final CryptoProperties cryptoProperties;

	record Result(CatalogueItem.Asset asset, byte[] cipherContent, byte[] cipherIndex, String masterWrappedBek) {
	}

	Result lock(String itemId, ContentType contentType, byte[] plaintext) {
		SecretKey bek = bookEncryptionKeys.generate();
		try {
			byte[] cipherContent = fileCipher.encrypt(bek, plaintext);

			CatalogueItem.Asset asset = new CatalogueItem.Asset();
			asset.setFormat(contentType);
			asset.setMimeType(mimeTypeFor(contentType));
			asset.setSizeBytes(plaintext.length);
			asset.setEncrypted(true);
			asset.setCipherLength(cipherContent.length);
			asset.setKeyId(cryptoProperties.masterKeyId());

			byte[] cipherIndex = contentType == ContentType.AUDIO ? null
					: tryBuildIndex(itemId, contentType, plaintext, bek, asset);

			String masterWrappedBek = bookEncryptionKeys.wrapWithMasterKey(bek);
			return new Result(asset, cipherContent, cipherIndex, masterWrappedBek);
		}
		finally {
			Arrays.fill(bek.getEncoded(), (byte) 0);
		}
	}

	private byte[] tryBuildIndex(String itemId, ContentType contentType, byte[] plaintext, SecretKey bek,
			CatalogueItem.Asset asset) {
		try {
			BuiltSearchIndex built = contentType == ContentType.PDF ? searchIndexService.buildPdfIndex(itemId, plaintext)
					: searchIndexService.buildEpubIndex(itemId, plaintext);
			asset.setHasSearchIndex(true);
			asset.setIndexTerms(built.termCount());
			return fileCipher.encrypt(bek, built.json());
		}
		catch (NoTextLayerException notSearchable) {
			// Not a failure - a scanned book that still reaches READY, just without an index.
			asset.setHasSearchIndex(false);
			asset.setIndexSkipReason(notSearchable.getMessage());
			return null;
		}
	}

	private static String mimeTypeFor(ContentType type) {
		return switch (type) {
			case PDF -> "application/pdf";
			case EPUB -> "application/epub+zip";
			case AUDIO -> "audio/mpeg";
		};
	}

}
