package com.tf.reader.admin.dto;

import java.time.Instant;

import com.tf.reader.common.model.RecordStatus;

/**
 * The Publisher schema as the console sees it. {@code itemCount} and
 * {@code collectionCount} are derived on every read and never stored on the
 * entity.
 *
 * <p>{@code entitlementStatus} is {@code null} for a caller who is not viewing as an institution,
 * and {@code "none"} for one who is and has no matching grant. Those are different facts and the
 * console shows different things for each, so they must not be collapsed.
 */
public record PublisherView(String id, String code, String name, String description, String logoUrl,
		RecordStatus status, long itemCount, long collectionCount, Instant createdAt,
		String entitlementStatus) {
}
