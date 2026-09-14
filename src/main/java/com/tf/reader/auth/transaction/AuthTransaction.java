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
 * login page and has no channel for us to influence it. Vestigial now that {@code SamlUserMapper}
 * never reads the assertion's identity either way, but harmless to leave: it still only ever
 * steers the mock's own response, nothing this backend decides anything from.
 *
 * <p>{@code deviceId} carries the device's own claimed identity forward, if it has signed in to
 * this institution before. A device presenting one it already holds is not a new seat and skips
 * the concurrent-session cap; a device presenting none is minted a fresh one by
 * {@code SamlUserMapper}. This is the only sense in which "who is signing in" survives the
 * redirect - there is no username or email behind it, by design.
 */
public record AuthTransaction(String id, String institutionId, String usernameHint, String deviceId,
		Instant createdAt, Instant expiresAt) {

	public boolean hasExpiredAt(Instant now) {
		return !now.isBefore(expiresAt);
	}
}
