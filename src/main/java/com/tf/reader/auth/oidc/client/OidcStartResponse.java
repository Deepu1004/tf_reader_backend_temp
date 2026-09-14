package com.tf.reader.auth.oidc.client;

import java.time.Instant;

/**
 * Response body for {@code POST /api/v1/auth/oidc/start}.
 *
 * <p>No institution to report: this is the individual sign-in flow, and an identity here
 * <b>is</b> the account - there is nothing for a caller to belong to.
 *
 * <p><b>Why this is not the token envelope.</b> The username and password are exchanged with the
 * provider and validated right here, but the resulting token pair is handed back once, by
 * redeeming {@code oidcTxnId} at {@code POST /api/v1/auth/oidc/token}, rather than in this same
 * response - so a client that only ever inspects a start response, a log line, or a stack trace
 * downstream of one is never in a position to see a token pair it did not explicitly ask for.
 *
 * <p>{@code oidcTxnId} is a one-time id, single use and short-lived, and is not itself a
 * credential - it proves nothing on its own, and redeeming it twice fails the second time.
 */
public record OidcStartResponse(
		String oidcTxnId,
		Instant expiresAt,
		Instant serverTime) {
}
