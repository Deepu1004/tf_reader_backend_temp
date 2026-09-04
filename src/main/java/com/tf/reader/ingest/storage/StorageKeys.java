package com.tf.reader.ingest.storage;

/**
 * Deterministic object keys, one per item per role. Fixed per item so a re-upload overwrites the
 * same objects rather than accumulating orphans - there is one {@code assets} list per item
 * today, an explicit replace, not version history.
 */
public final class StorageKeys {

	private StorageKeys() {
	}

	/** The raw bytes exactly as uploaded, staged for {@code IngestProcessor} to pick up. */
	public static String staging(String itemId) {
		return "items/" + itemId + "/upload";
	}

	/** Final asset bytes: ciphertext for a locked asset, identical plaintext otherwise. */
	public static String content(String itemId) {
		return "items/" + itemId + "/content";
	}

	/** Encrypted search index bytes. Only written when one was actually built. */
	public static String index(String itemId) {
		return "items/" + itemId + "/index";
	}

	/** The cover image, always plaintext - a cover is never a secret. */
	public static String cover(String itemId) {
		return "items/" + itemId + "/cover";
	}

}
