package com.tf.reader.crypto.service;

import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.tf.reader.crypto.api.KmsClient;

/**
 * The first real {@link KmsClient}: resolves a publisher's own vault key via
 * {@link PublisherVaultKeyStore}, falling back to T&F's shared master key when the publisher
 * hasn't configured one. {@code vaultRef} here is a publisher id, not a call to any external
 * service - there is no real HSM/vault behind this, per the plan's "publisher's own AES key"
 * framing rather than a real external KMS integration.
 */
@Service
class MongoBackedKmsClient implements KmsClient {

	private static final String AES = "AES";
	private static final String AES_WRAP_TRANSFORMATION = "AESWrap";

	private final SecretKey masterKey;
	private final PublisherVaultKeyStore vaultKeyStore;

	MongoBackedKmsClient(SecretKey masterKey, PublisherVaultKeyStore vaultKeyStore) {
		this.masterKey = masterKey;
		this.vaultKeyStore = vaultKeyStore;
	}

	@Override
	public String wrapKey(String vaultRef, byte[] plaintextKey) {
		SecretKey effectiveKey = effectiveKeyFor(vaultRef);
		try {
			Cipher cipher = Cipher.getInstance(AES_WRAP_TRANSFORMATION);
			cipher.init(Cipher.WRAP_MODE, effectiveKey);
			return Base64.getEncoder().encodeToString(cipher.wrap(new SecretKeySpec(plaintextKey, AES)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to wrap the key for publisher " + vaultRef, e);
		}
	}

	@Override
	public byte[] unwrapKey(String vaultRef, String wrappedKey) {
		SecretKey effectiveKey = effectiveKeyFor(vaultRef);
		try {
			byte[] wrapped = Base64.getDecoder().decode(wrappedKey);
			Cipher cipher = Cipher.getInstance(AES_WRAP_TRANSFORMATION);
			cipher.init(Cipher.UNWRAP_MODE, effectiveKey);
			return ((SecretKey) cipher.unwrap(wrapped, AES, Cipher.SECRET_KEY)).getEncoded();
		} catch (IllegalArgumentException | GeneralSecurityException e) {
			throw new IllegalStateException(
					"wrappedKey is not a value this publisher's effective key can unwrap.", e);
		}
	}

	/** The publisher's own key if they've configured one, otherwise T&F's shared master key. */
	private SecretKey effectiveKeyFor(String vaultRef) {
		return vaultKeyStore.find(vaultRef).orElse(masterKey);
	}

}
