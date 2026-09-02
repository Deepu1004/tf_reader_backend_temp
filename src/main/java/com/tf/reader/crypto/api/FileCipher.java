package com.tf.reader.crypto.api;

import javax.crypto.SecretKey;

/**
 * Whole-file AES-256-GCM encryption under a book's BEK. Output layout is
 * {@code nonce(12) || ciphertext || tag(16)} — 28 bytes of overhead over the plaintext, whatever
 * its size.
 *
 * <p>Never used for open-access content (a key handed to an anonymous reader protects nothing).
 * Locked audio (SUBSCRIPTION/ELITE) does go through this cipher like any other locked asset —
 * whole-file encryption still can't be seeked into without a full decrypt first, which is a real
 * cost the device pays on playback, accepted deliberately rather than leaving locked audio
 * unencrypted. That tier/format decision belongs to the caller ({@code ingest.service.TierRules}),
 * not here.
 */
public interface FileCipher {

	/** Encrypts {@code plaintext} under a fresh 12-byte nonce from {@code SecureRandom.getInstanceStrong()}. */
	byte[] encrypt(SecretKey bek, byte[] plaintext);

	/**
	 * Decrypts a value produced by {@link #encrypt}.
	 *
	 * @throws IllegalStateException if the GCM authentication tag does not verify — the
	 *         ciphertext was truncated, corrupted, or encrypted under a different key
	 */
	byte[] decrypt(SecretKey bek, byte[] ciphertext);

}
