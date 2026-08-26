package com.tf.reader.crypto.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.junit.jupiter.api.Test;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.crypto.CryptoProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookEncryptionKeysImplTest {

	private static final int DEVICE_KEY_MIN_BITS = 2048;

	private final SecretKey masterKey = aesKey();
	private final BookEncryptionKeysImpl keys =
			new BookEncryptionKeysImpl(masterKey, new CryptoProperties(null, null, DEVICE_KEY_MIN_BITS));

	// Mirrors ContentAccessGrantImplTest's OAEP_SHA256 spec exactly: this is what proves
	// rewrapForDevice's output is unwrappable by a real client, not just self-consistent.
	private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
			"SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

	private static SecretKey aesKey() {
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
			keyGenerator.init(256);
			return keyGenerator.generateKey();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static KeyPair rsaKeyPair(int bits) {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(bits);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] unwrapOnDevice(String wrappedBekBase64, PrivateKey devicePrivateKey) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		cipher.init(Cipher.DECRYPT_MODE, devicePrivateKey, OAEP_SHA256);
		return cipher.doFinal(Base64.getDecoder().decode(wrappedBekBase64));
	}

	@Test
	void roundTripsABekThroughTheMasterKey() {
		SecretKey bek = keys.generate();

		String masterWrappedBek = keys.wrapWithMasterKey(bek);
		SecretKey unwrapped = keys.unwrapWithMasterKey(masterWrappedBek);

		assertThat(unwrapped.getEncoded()).isEqualTo(bek.getEncoded());
	}

	@Test
	void aDeviceCanUnwrapTheBekItWasWrappedFor() throws Exception {
		SecretKey bek = keys.generate();
		String masterWrappedBek = keys.wrapWithMasterKey(bek);
		KeyPair device = rsaKeyPair(DEVICE_KEY_MIN_BITS);

		String wrappedBek = keys.rewrapForDevice(masterWrappedBek, device.getPublic().getEncoded());

		assertThat(unwrapOnDevice(wrappedBek, device.getPrivate())).isEqualTo(bek.getEncoded());
	}

	@Test
	void rejectsADeviceKeyUnderTheConfiguredMinimum() {
		String masterWrappedBek = keys.wrapWithMasterKey(keys.generate());
		KeyPair tooSmall = rsaKeyPair(1024);

		assertThatThrownBy(() -> keys.rewrapForDevice(masterWrappedBek, tooSmall.getPublic().getEncoded()))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_DEVICE_PUBLIC_KEY));
	}

	@Test
	void rejectsADevicePublicKeyThatIsNotValidRsaSpki() {
		String masterWrappedBek = keys.wrapWithMasterKey(keys.generate());

		assertThatThrownBy(() -> keys.rewrapForDevice(masterWrappedBek, new byte[] {1, 2, 3}))
				.isInstanceOf(ApiException.class);
	}

}
