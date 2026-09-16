package com.tf.reader.crypto.api;

/**
 * Asks a publisher's own key vault to lock or unlock a key, instead of doing it locally with T&F's
 * master key. See {@code temp/multi-tenancy/multi-tenancy-backend.md} for the full plan this
 * belongs to.
 *
 * <p>Not wired into anything yet: {@code BookEncryptionKeysImpl} still does every wrap/unwrap
 * locally against T&F's one master key. Method names mirror {@link BookEncryptionKeys} so that,
 * once a real implementation and the decision to call it exist, the swap is a small diff rather
 * than a new vocabulary.
 *
 * @throws com.tf.reader.common.error.ApiException with {@code KMS_UNAVAILABLE} once that error code
 *         exists and something actually implements this against a real vault
 */
public interface KmsClient {

	/** Asks {@code vaultRef}'s vault to wrap {@code plaintextKey}, returning an opaque wrapped value. */
	String wrapKey(String vaultRef, byte[] plaintextKey);

	/** Asks {@code vaultRef}'s vault to unwrap a value previously returned by {@link #wrapKey}. */
	byte[] unwrapKey(String vaultRef, String wrappedKey);

}
