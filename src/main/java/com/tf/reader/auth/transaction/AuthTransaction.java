package com.tf.reader.auth.transaction;

import java.time.Instant;

/**
 * One in-flight institutional sign-in.
 *
 * <p>This is the record that survives the round trip to the IdP. The {@code id} is the only
 * part that ever leaves the server, and it is opaque: it carries no institution, no user and
 * no other meaning, so nothing can be learned or forged from it.
 *
 * <p>{@code usernameHint} exists for exactly one consumer: the local mock IdP, which has no
 * login page of its own and otherwise always asserts the same configured identity. It tells the
 * mock which seeded user to assert instead - never the real IdP, which decides that on its own
 * login page and has no channel for us to influence it. It is never read by
 * {@code SamlUserMapper} or {@code SamlAuthenticationService}: identity still comes only from the
 * signed assertion, exactly as before this field existed.
 */
public record AuthTransaction(String id, String institutionId, String usernameHint, Instant createdAt,
		Instant expiresAt) {

	public boolean hasExpiredAt(Instant now) {
		return !now.isBefore(expiresAt);
	}
}
