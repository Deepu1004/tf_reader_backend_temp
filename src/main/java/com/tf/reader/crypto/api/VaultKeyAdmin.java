package com.tf.reader.crypto.api;

/**
 * Lets the admin surface set or clear a publisher's own vault key, without exposing where that
 * key is actually stored (their own Mongo, once configured, or T&F's shared database otherwise -
 * see {@code crypto.service.PublisherVaultKeyStore}). Separate from {@link KmsClient}, which is
 * the runtime wrap/unwrap seam; this is the administrative one.
 */
public interface VaultKeyAdmin {

	/** @param rawAesKey exactly 32 bytes; the caller has already validated this. */
	void setKey(String publisherId, byte[] rawAesKey);

	/** Reverts the publisher to T&F's shared master key. */
	void clearKey(String publisherId);

}
