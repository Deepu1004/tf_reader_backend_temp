package com.tf.reader.auth.token;

import java.time.Instant;

/**
 * A minted access token and the two times a caller needs to reason about it.
 *
 * <p>{@code expiresAt} is here because the app has no refresh token: it must know when to send
 * the user back through sign-in, and it must not work that out from its own clock.
 */
public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {
}
