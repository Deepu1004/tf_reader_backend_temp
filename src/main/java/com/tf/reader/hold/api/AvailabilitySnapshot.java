package com.tf.reader.hold.api;

/**
 * Snapshot describing current availability and queue state for a copy-limited title.
 */
public record AvailabilitySnapshot(
		int copiesTotal,
		int copiesFree,
		int queueLength,
		Integer myPosition
) {}
