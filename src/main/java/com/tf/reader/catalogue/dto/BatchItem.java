package com.tf.reader.catalogue.dto;

import java.util.List;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;

/**
 * The BatchItem schema: one catalogue item as the reader app may see it. {@code totalCopies}
 * comes from the caller's {@code EntitlementDecision}, not the item itself - it is a per-caller
 * copy limit, not a property of the book. Never carries {@code storageKey}, {@code indexKey} or
 * {@code wrappedBek}; those fields do not exist on this type at all.
 */
public record BatchItem(String id, String title, List<String> authors, String coverUrl, String isbn,
		ContentType contentType, AccessTier accessTier, Integer totalCopies, boolean hasSearchIndex) {
}
