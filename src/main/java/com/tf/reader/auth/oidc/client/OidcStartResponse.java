package com.tf.reader.auth.oidc.client;

import java.time.Instant;

/**
 * Response body for {@code POST /api/v1/auth/oidc/start}.
 *
 * <p>No institution to report: this is the individual sign-in flow, and an identity here
 * <b>is</b> the account - there is nothing for a caller to belong to.
 *
 * <p><b>Why this is not the token envelope.</b> OIDC's authorization code flow is a browser
 * redirect protocol: the code is delivered to our callback by the identity provider, not
 * returned down this JSON call, so no endpoint can both start OIDC and hand back a session. The
 * token is minted at the callback, once an ID token has been validated.
 *
 * <p>{@code authTxnId} is echoed back for the client to correlate its own state. It is the value
 * that travels as the OAuth 2.0 {@code state} parameter, and it is not a credential - it proves
 * nothing on its own.
 */
public record OidcStartResponse(
		String authTxnId,
		String authorizationUrl,
		Instant expiresAt,
		Instant serverTime) {
}
