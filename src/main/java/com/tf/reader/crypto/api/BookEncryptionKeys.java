package com.tf.reader.crypto.api;

import javax.crypto.SecretKey;

/**
 * Generates, wraps and unwraps book encryption keys (BEKs). See
 * {@code temp/w3-06-encryption/encryption-masterkeys.md} for the full key hierarchy this
 * implements, and {@code temp/multi-tenancy/multi-tenancy-backend.md} for why every wrap/unwrap
 * below takes a {@code publisherId}: the key that's actually in effect for a publisher is their
 * own, once they've configured one, or T&F's shared master key otherwise. Which one applies is
 * resolved by {@link com.tf.reader.crypto.api.KmsClient}, never decided by the caller.
 *
 * <p>A BEK returned by {@link #generate()} or {@link #unwrapWithMasterKey(String, String)} is live
 * key material: the caller owns it from that point on, including zeroing it (for example
 * {@code Arrays.fill(bek.getEncoded(), (byte) 0)}) the moment it is no longer needed.
 * {@link #rewrapForDevice(String, String, byte[])} does not have this problem — it never returns a
 * live BEK to its caller at all.
 */
public interface BookEncryptionKeys {

	/** A fresh 256-bit AES key, from {@code KeyGenerator}/{@code SecureRandom}, never a fixed value. */
	SecretKey generate();

	/**
	 * Wraps {@code bek} under {@code publisherId}'s effective key, for storage as
	 * {@code masterWrappedBek}. That value must never be returned by any endpoint — only
	 * {@link #rewrapForDevice} output is.
	 */
	String wrapWithMasterKey(String publisherId, SecretKey bek);

	/** Unwraps a stored {@code masterWrappedBek} back to a live BEK, under {@code publisherId}'s effective key. */
	SecretKey unwrapWithMasterKey(String publisherId, String masterWrappedBek);

	/**
	 * Unwraps {@code masterWrappedBek} under {@code publisherId}'s effective key and immediately
	 * re-wraps the BEK to a device's RSA public key, returning the result for that request's
	 * {@code wrappedBek}. This is the one method a read-path caller should use, rather than
	 * composing {@link #unwrapWithMasterKey} with a manual device wrap: it keeps the only moment a
	 * plaintext BEK exists on this server inside one call, with the transient bytes zeroed before
	 * it returns.
	 *
	 * @param devicePublicKeySpki raw X.509 SubjectPublicKeyInfo bytes of an RSA public key
	 * @throws com.tf.reader.common.error.ApiException with {@code INVALID_DEVICE_PUBLIC_KEY} if
	 *         the key does not parse as RSA or is under {@code tf.crypto.device-key-min-bits}
	 */
	String rewrapForDevice(String publisherId, String masterWrappedBek, byte[] devicePublicKeySpki);

}
