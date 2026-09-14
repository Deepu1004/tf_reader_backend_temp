package com.tf.reader.crypto.service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.crypto.CryptoProperties;
import com.tf.reader.crypto.api.BookEncryptionKeys;
import com.tf.reader.crypto.api.KmsClient;

/**
 * See {@link BookEncryptionKeys} for the contract. Wrapping/unwrapping is delegated to
 * {@link KmsClient}, which resolves whichever key is actually in effect for the named publisher -
 * their own, once configured, or T&F's shared master key otherwise. This class no longer holds a
 * key itself; RSA-OAEP-256 to a device key is still the one place a plaintext BEK's bytes
 * genuinely have to exist, and that window is kept as short as the language allows - see
 * {@link #rewrapForDevice}.
 */
@Service
class BookEncryptionKeysImpl implements BookEncryptionKeys {

	private static final String AES = "AES";
	private static final int BEK_BITS = 256;
	private static final String RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

	// Java's transformation string names only the OAEP digest; without this explicit spec, MGF1
	// silently defaults to SHA-1, and every device wrap becomes undecryptable by a real client.
	private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
			"SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

	private final KmsClient kmsClient;
	private final int deviceKeyMinBits;

	BookEncryptionKeysImpl(KmsClient kmsClient, CryptoProperties properties) {
		this.kmsClient = kmsClient;
		this.deviceKeyMinBits = properties.deviceKeyMinBits();
	}

	@Override
	public SecretKey generate() {
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
			keyGenerator.init(BEK_BITS);
			return keyGenerator.generateKey();
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("AES is always available", e);
		}
	}

	@Override
	public String wrapWithMasterKey(String publisherId, SecretKey bek) {
		byte[] bekBytes = bek.getEncoded();
		try {
			return kmsClient.wrapKey(publisherId, bekBytes);
		} finally {
			Arrays.fill(bekBytes, (byte) 0);
		}
	}

	@Override
	public SecretKey unwrapWithMasterKey(String publisherId, String masterWrappedBek) {
		if (masterWrappedBek == null) {
			throw new IllegalStateException("masterWrappedBek must not be null.");
		}
		byte[] raw = kmsClient.unwrapKey(publisherId, masterWrappedBek);
		try {
			return new SecretKeySpec(raw, AES);
		} finally {
			Arrays.fill(raw, (byte) 0);
		}
	}

	@Override
	public String rewrapForDevice(String publisherId, String masterWrappedBek, byte[] devicePublicKeySpki) {
		PublicKey deviceKey = parseAndValidate(devicePublicKeySpki);

		SecretKey bek = unwrapWithMasterKey(publisherId, masterWrappedBek);
		byte[] bekBytes = bek.getEncoded();
		try {
			Cipher cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, deviceKey, OAEP_SHA256);
			return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(bekBytes));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to wrap BEK to the device key.", e);
		} finally {
			// The only moment a plaintext BEK's bytes exist in our hands: gone the instant this
			// method returns, whether it returns a value or throws.
			Arrays.fill(bekBytes, (byte) 0);
		}
	}

	private PublicKey parseAndValidate(byte[] devicePublicKeySpki) {
		try {
			PublicKey key = KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(devicePublicKeySpki));
			if (((RSAPublicKey) key).getModulus().bitLength() < deviceKeyMinBits) {
				throw new ApiException(ErrorCode.INVALID_DEVICE_PUBLIC_KEY,
						"devicePublicKey must be at least " + deviceKeyMinBits + " bits.");
			}
			return key;
		} catch (IllegalArgumentException | GeneralSecurityException e) {
			throw new ApiException(ErrorCode.INVALID_DEVICE_PUBLIC_KEY,
					"devicePublicKey must be base64 of a raw SPKI-encoded RSA public key.");
		}
	}

}
