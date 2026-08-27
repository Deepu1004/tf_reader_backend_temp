package com.tf.reader.crypto.api;

import javax.crypto.SecretKey;

/**
 * Generates, wraps and unwraps book encryption keys (BEKs). See
 * {@code temp/w3-06-encryption/encryption-masterkeys.md} for the full key hierarchy this
 * implements.
 *
 * <p>A BEK returned by {@link #generate()} or {@link #unwrapWithMasterKey(String)} is live key
 * material: the caller owns it from that point on, including zeroing it (for example
 * {@code Arrays.fill(bek.getEncoded(), (byte) 0)}) the moment it is no longer needed.
 * {@link #rewrapForDevice(String, byte[])} does not have this problem — it never returns a live
 * BEK to its caller at all.
 */
public interface BookEncryptionKeys {

	/** A fresh 256-bit AES key, from {@code KeyGenerator}/{@code SecureRandom}, never a fixed value. */
	SecretKey generate();

	/**
	 * Wraps {@code bek} under the server's master key, for storage as {@code masterWrappedBek}.
	 * That value must never be returned by any endpoint — only {@link #rewrapForDevice} output is.
	 */
	String wrapWithMasterKey(SecretKey bek);

	/** Unwraps a stored {@code masterWrappedBek} back to a live BEK. */
	SecretKey unwrapWithMasterKey(String masterWrappedBek);

	/**
	 * Unwraps {@code masterWrappedBek} and immediately re-wraps the BEK to a device's RSA public
	 * key, returning the result for that request's {@code wrappedBek}. This is the one method a
	 * read-path caller should use, rather than composing {@link #unwrapWithMasterKey} with a
	 * manual device wrap: it keeps the only moment a plaintext BEK exists on this server inside
	 * one call, with the transient bytes zeroed before it returns.
	 *
	 * @param devicePublicKeySpki raw X.509 SubjectPublicKeyInfo bytes of an RSA public key
	 * @throws com.tf.reader.common.error.ApiException with {@code INVALID_DEVICE_PUBLIC_KEY} if
	 *         the key does not parse as RSA or is under {@code tf.crypto.device-key-min-bits}
	 */
	String rewrapForDevice(String masterWrappedBek, byte[] devicePublicKeySpki);

}
