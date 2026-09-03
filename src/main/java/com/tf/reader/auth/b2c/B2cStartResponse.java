package com.tf.reader.auth.b2c;

import java.time.Instant;

/**
 * Response body for {@code POST /api/v1/auth/b2c/start}.
 *
 * <p>Field for field {@link com.tf.reader.auth.oidc.client.OidcStartResponse} minus
 * {@code institution}: this flow has none to report - it authenticates an individual, not a
 * member of anything.
 */
public record B2cStartResponse(
		String authTxnId,
		String authorizationUrl,
		Instant expiresAt,
		Instant serverTime) {
}
