package com.tf.reader.catalogue.dto;

import java.util.List;

/**
 * The BatchItemsResponse schema. {@code notFound} and {@code denied} are kept as two separate
 * lists on purpose: one is "gone" (no such item, or archived), the other is "exists, but not
 * yours" - a client that merged them could not tell the two apart.
 */
public record BatchItemsResponse(List<BatchItem> items, List<String> notFound, List<String> denied) {
}
