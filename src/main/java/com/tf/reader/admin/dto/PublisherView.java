package com.tf.reader.admin.dto;

import java.time.Instant;

import com.tf.reader.common.model.RecordStatus;

/**
 * The Publisher schema as the console sees it. {@code itemCount} and
 * {@code collectionCount} are derived on every read and never stored on the
 * entity.
 */
public record PublisherView(String id, String code, String name, String description, String logoUrl,
		RecordStatus status, long itemCount, long collectionCount, Instant createdAt) {
}
