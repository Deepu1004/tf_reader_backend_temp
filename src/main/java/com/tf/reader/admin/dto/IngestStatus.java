package com.tf.reader.admin.dto;

import java.time.Instant;

import com.tf.reader.catalogue.entity.ContentState;

/**
 * Response body for both ingest endpoints - matches the contract's {@code IngestStatus} schema
 * exactly. {@code contentState}/{@code contentError} describe the one part named by
 * {@code partNumber}, which moves QUEUED to PROCESSING to READY, or to FAILED with
 * {@code contentError} set.
 */
public record IngestStatus(String itemId, AssetFormat format, int partNumber, ContentState contentState,
		String contentError, Instant updatedAt) {
}
