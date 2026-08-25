package com.tf.reader.ingest.index;

import com.tf.reader.sync.model.Locator;

/**
 * A flat build-time row: one word plus where it occurs. The extractor emits these in reading order;
 * {@link BookSearchIndex} groups them by {@code word}. Mirrors the mobile {@code IndexEntry}.
 */
record IndexEntry(int seq, String word, String chapterId, Locator locator, String snippet) {

	/** The posting for this entry — the same fields minus the word it is grouped under. */
	Posting toPosting() {
		return new Posting(chapterId, locator, snippet, seq);
	}
}
