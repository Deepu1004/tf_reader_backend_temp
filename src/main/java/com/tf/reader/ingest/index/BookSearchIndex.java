package com.tf.reader.ingest.index;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full per-book search artifact. Serialized to JSON, encrypted under the book's BEK (or shipped
 * plaintext for open-access) and bundled with the download; only this builder and the mobile
 * {@code queryIndex} understand its contents — to storage it is an opaque blob. Field order and
 * shape mirror the mobile contract's {@code BookSearchIndex}.
 *
 * <p>{@code version} is the index-format version. It is 2: postings carry {@code seq} for
 * phrase/adjacency matching. A pre-2 index has no seq and degrades to single-word lookup on the
 * client; the bump is the migration signal.
 */
record BookSearchIndex(String bookId, String format, int version, Map<String, List<Posting>> index) {

	static final int VERSION = 2;

	static final String FORMAT_EPUB = "EPUB";

	static final String FORMAT_PDF = "PDF";

	/**
	 * Group flat entries by normalized word, preserving reading order within each word (entries
	 * arrive in reading order, so no later sort is needed). A {@link LinkedHashMap} keeps word keys
	 * in first-seen order for a stable, diffable serialization.
	 */
	static BookSearchIndex fromEntries(String bookId, String format, List<IndexEntry> entries) {
		Map<String, List<Posting>> index = new LinkedHashMap<>();
		for (IndexEntry entry : entries) {
			index.computeIfAbsent(entry.word(), w -> new java.util.ArrayList<>()).add(entry.toPosting());
		}
		return new BookSearchIndex(bookId, format, VERSION, index);
	}
}
