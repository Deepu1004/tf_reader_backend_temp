package com.tf.reader.ingest.storage;

/**
 * Deterministic object keys, one per item per part per role. Fixed per part so a re-upload of
 * the same chapter overwrites the same objects rather than accumulating orphans - there is one
 * {@code Part} per (asset, partNumber) today, an explicit replace, not version history.
 *
 * <p>Part 1's key is unchanged from before parts existed ({@code items/{itemId}/content}, not
 * {@code items/{itemId}/part1/content}) - real objects for the already-ingested dev fixtures live
 * at that exact path in object storage, and nothing about a plain, single-part book should ever
 * need to move. Only part 2 and beyond, which never existed before this, get the new
 * {@code /partN/} segment.
 */
public final class StorageKeys {

	private StorageKeys() {
	}

	/** The raw bytes exactly as uploaded, staged for {@code IngestProcessor} to pick up. */
	public static String staging(String itemId, int partNumber) {
		return prefix(itemId, partNumber) + "/upload";
	}

	/** Final part bytes: ciphertext for a locked asset, identical plaintext otherwise. */
	public static String content(String itemId, int partNumber) {
		return prefix(itemId, partNumber) + "/content";
	}

	/** Encrypted search index bytes. Only written when one was actually built. */
	public static String index(String itemId, int partNumber) {
		return prefix(itemId, partNumber) + "/index";
	}

	/** The cover image, always plaintext - a cover is never a secret, and never split by part. */
	public static String cover(String itemId) {
		return "items/" + itemId + "/cover";
	}

	private static String prefix(String itemId, int partNumber) {
		return partNumber == 1 ? "items/" + itemId : "items/" + itemId + "/part" + partNumber;
	}

}
