package com.tf.reader.loan.api;

import java.time.Instant;

import com.tf.reader.catalogue.api.AccessLevel;

/**
 * Read view of a created or existing active license.
 */
public record LicenseView(
		String licenseId,
		String userId,
		String itemId,
		AccessLevel accessLevel,
		boolean canPersist,
		Instant expiresAt,
		String leaseId
) {}
