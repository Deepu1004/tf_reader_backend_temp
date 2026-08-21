package com.tf.reader.reading.api;

import java.time.Instant;

/**
 * An opaque handle to an active copy lease held in Redis.
 *
 * @param token      The unique random token representing this lease instance.
 * @param scope      The institution ID (or scope identifier) owning the lease pool.
 * @param itemId     The catalogue item ID.
 * @param expiresAt  The expiration timestamp of this lease claim.
 */
public record LeaseHandle(
		String token,
		String scope,
		String itemId,
		Instant expiresAt
) {
	/** Alias for token for backward-compatibility with leaseId callers. */
	public String leaseId() {
		return token;
	}
}
