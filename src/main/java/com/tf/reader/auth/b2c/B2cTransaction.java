package com.tf.reader.auth.b2c;

import java.time.Instant;

/**
 * One in-flight B2C sign-in: an individual authenticating with no institution involved at all.
 *
 * <p>The individual counterpart of {@link com.tf.reader.auth.oidc.client.OidcTransaction} minus
 * the institution it carries - there is nothing here to recover an institution from, because
 * there isn't one. {@code id} and {@code state} are still different values for the same reason
 * they are there: {@code state} is the correlator on the wire, {@code id} is the handle the
 * client uses to correlate its own UI, and neither is a credential.
 */
public record B2cTransaction(
		String id,
		String state,
		String nonce,
		Instant createdAt,
		Instant expiresAt) {

	public boolean hasExpiredAt(Instant now) {
		return !now.isBefore(expiresAt);
	}
}
