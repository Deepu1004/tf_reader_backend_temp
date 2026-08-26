package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.crypto.CryptoProperties;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.crypto.api.FileCipher;
import com.tf.reader.ingest.index.BuiltSearchIndex;
import com.tf.reader.ingest.index.NoTextLayerException;
import com.tf.reader.ingest.index.SearchIndexService;

/**
 * Wiring only - {@code BookEncryptionKeys}, {@code FileCipher} and {@code SearchIndexService} are
 * already tested on their own. This proves {@code AssetLocker} calls them in the right order with
 * the right arguments, and that a scanned PDF reaches a normal result rather than throwing.
 *
 * <p>{@code NoTextLayerException}'s real constructor is package-private in {@code ingest.index},
 * so it is mocked here rather than constructed - {@code Mockito.mock(...)} bypasses the
 * constructor entirely, which needs no cross-package access.
 */
class AssetLockerTest {

	private final BookEncryptionKeys keys = mock(BookEncryptionKeys.class);
	private final FileCipher cipher = mock(FileCipher.class);
	private final SearchIndexService searchIndex = mock(SearchIndexService.class);
	private final CryptoProperties cryptoProperties = new CryptoProperties("k", "master-v1", null);
	private final AssetLocker locker = new AssetLocker(keys, cipher, searchIndex, cryptoProperties);

	private static final SecretKeySpec BEK = new SecretKeySpec(new byte[32], "AES");

	@Test
	void encryptsAndIndexesAPdfThenWrapsUnderTheMasterKey() {
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(BEK, "plain".getBytes())).thenReturn("cipher".getBytes());
		when(searchIndex.buildPdfIndex("item_1", "plain".getBytes()))
				.thenReturn(new BuiltSearchIndex("index-json".getBytes(), 42));
		when(cipher.encrypt(BEK, "index-json".getBytes())).thenReturn("cipher-index".getBytes());
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.PDF, "plain".getBytes());

		assertThat(result.cipherContent()).isEqualTo("cipher".getBytes());
		assertThat(result.cipherIndex()).isEqualTo("cipher-index".getBytes());
		assertThat(result.masterWrappedBek()).isEqualTo("wrapped");
		assertThat(result.asset().isEncrypted()).isTrue();
		assertThat(result.asset().isHasSearchIndex()).isTrue();
		assertThat(result.asset().getIndexTerms()).isEqualTo(42);
		assertThat(result.asset().getKeyId()).isEqualTo("master-v1");
	}

	@Test
	void aScannedPdfStillReturnsAResultInsteadOfThrowing() {
		NoTextLayerException scanned = mock(NoTextLayerException.class);
		when(scanned.getMessage()).thenReturn("only 2 of 40 pages carried extractable text");
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(BEK, "plain".getBytes())).thenReturn("cipher".getBytes());
		when(searchIndex.buildPdfIndex(any(), any())).thenThrow(scanned);
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.PDF, "plain".getBytes());

		assertThat(result.cipherIndex()).isNull();
		assertThat(result.asset().isHasSearchIndex()).isFalse();
		assertThat(result.asset().getIndexSkipReason()).isNotBlank();
	}

	@Test
	void audioIsEncryptedButNeverIndexed() {
		when(keys.generate()).thenReturn(BEK);
		when(cipher.encrypt(BEK, "plain".getBytes())).thenReturn("cipher".getBytes());
		when(keys.wrapWithMasterKey(BEK)).thenReturn("wrapped");

		AssetLocker.Result result = locker.lock("item_1", ContentType.AUDIO, "plain".getBytes());

		verify(searchIndex, never()).buildPdfIndex(any(), any());
		verify(searchIndex, never()).buildEpubIndex(any(), any());
		assertThat(result.cipherIndex()).isNull();
	}

}
