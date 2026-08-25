package com.tf.reader.crypto;

import java.util.Base64;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class CryptoConfigTest {

	private static final String VALID_MASTER_KEY_BASE64 =
			Base64.getEncoder().encodeToString(new byte[32]);

	private final CryptoConfig config = new CryptoConfig();

	@Test
	void buildsAThirtyTwoByteAesKeyWhenTheMasterKeyIsConfigured() {
		SecretKey masterKey = config.masterKey(new CryptoProperties(VALID_MASTER_KEY_BASE64, null, null));

		assertThat(masterKey.getAlgorithm()).isEqualTo("AES");
		assertThat(masterKey.getEncoded()).hasSize(32);
	}

	@Test
	void failsFastWhenTheMasterKeyIsMissing() {
		assertThatIllegalStateException()
				.isThrownBy(() -> config.masterKey(new CryptoProperties(null, null, null)))
				.withMessageContaining("TF_MASTER_KEY");
	}

	@Test
	void failsFastWhenTheMasterKeyIsBlank() {
		assertThatIllegalStateException()
				.isThrownBy(() -> config.masterKey(new CryptoProperties(" ", null, null)));
	}

	@Test
	void failsFastWhenTheMasterKeyIsNotValidBase64() {
		assertThatIllegalStateException()
				.isThrownBy(() -> config.masterKey(new CryptoProperties("not-valid-base64!!", null, null)));
	}

	@Test
	void failsFastWhenTheMasterKeyIsTheWrongLength() {
		String tooShort = Base64.getEncoder().encodeToString(new byte[16]);

		assertThatIllegalStateException()
				.isThrownBy(() -> config.masterKey(new CryptoProperties(tooShort, null, null)))
				.withMessageContaining("32");
	}

}
