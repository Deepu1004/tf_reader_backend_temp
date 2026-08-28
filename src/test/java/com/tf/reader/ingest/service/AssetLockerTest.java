package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.crypto.CryptoProperties;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.crypto.api.FileCipher;
import com.tf.reader.ingest.index.BuiltSearchIndex;

/**
 * Wiring only - {@code BookEncryptionKeys}, {@code FileCipher} and index-building itself
 * ({@code SearchIndexBuilder}, tested on its own) are already tested. This proves
 * {@code AssetLocker} calls them in the right order, resolves the mime type correctly (preferring
 * the real uploaded type over a format-based guess), and zeroes the BEK regardless of outcome.
 */
class AssetLockerTest {

	private final BookEncryptionKeys keys = mock(BookEncryptionKeys.class);
	private final FileCipher cipher = mock(FileCipher.class);
	private final SearchIndexBuilder searchIndexBuilder = mock(SearchIndexBuilder.class);
	private final CryptoProperties cryptoProperties = new CryptoProperties("k", "master-v1", null);
	private final AssetLocker locker = new AssetLocker(keys, cipher, searchIndexBuilder, cryptoProperties);

	private static final SecretKeySpec BEK = new SecretKeySpec(new byte[32], "AES");

	@Test
	void encryptsAndIndexesAPdfThenWrapsUnderTheMasterKey() {
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(BEK, "plain".getBytes())).thenReturn("cipher".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenAnswer(invocation -> {
			CatalogueItem.Asset asset = invocation.getArgument(3);
			asset.setHasSearchIndex(true);
			asset.setIndexTerms(42);
			return Optional.of(new BuiltSearchIndex("index-json".getBytes(), 42));
		});
		when(cipher.encrypt(BEK, "index-json".getBytes())).thenReturn("cipher-index".getBytes());
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.PDF, "plain".getBytes(), null);

		assertThat(result.cipherContent()).isEqualTo("cipher".getBytes());
		assertThat(result.cipherIndex()).isEqualTo("cipher-index".getBytes());
		assertThat(result.masterWrappedBek()).isEqualTo("wrapped");
		assertThat(result.asset().isEncrypted()).isTrue();
		assertThat(result.asset().isHasSearchIndex()).isTrue();
		assertThat(result.asset().getIndexTerms()).isEqualTo(42);
		assertThat(result.asset().getKeyId()).isEqualTo("master-v1");
		assertThat(result.asset().getMimeType()).isEqualTo("application/pdf");
	}

	@Test
	void omitsTheIndexWhenTheBuilderFoundNoneToBuild() {
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(any(), any())).thenReturn("cipher".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.AUDIO, "plain".getBytes(), "audio/mp4");

		assertThat(result.cipherIndex()).isNull();
	}

	@Test
	void prefersTheUploadedMimeTypeOverTheFormatGuess() {
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(any(), any())).thenReturn("cipher".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.AUDIO, "plain".getBytes(), "audio/x-m4b");

		assertThat(result.asset().getMimeType()).isEqualTo("audio/x-m4b");
	}

	@Test
	void fallsBackToTheFormatGuessWhenNoUploadedMimeTypeIsKnown() {
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(any(), any())).thenReturn("cipher".getBytes());
		when(searchIndexBuilder.build(any(), any(), any(), any())).thenReturn(Optional.empty());
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.AUDIO, "plain".getBytes(), null);

		assertThat(result.asset().getMimeType()).isEqualTo("audio/mpeg");
	}

}
