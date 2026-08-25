package com.tf.reader.ingest.index;

import com.tf.reader.sync.model.Locator;

/**
 * One occurrence of a word in the book — the value stored per hit in the index. Mirrors the mobile
 * contract's {@code Posting} (field order matters: the serialized JSON is consumed by the mobile
 * {@code queryIndex}). Reuses {@link Locator} rather than redefining a position type, so bookmarks,
 * highlights and search hits all navigate through the one shape.
 *
 * <p>{@code seq} is the token ordinal in reading order; phrase/adjacency matching on the client is a
 * token-sequence property, not a character-distance one, so it must be present and correct.
 */
record Posting(String chapterId, Locator locator, String snippet, int seq) {
}
