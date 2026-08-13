package com.tf.reader.auth.model;

/**
 * The personas the product recognises.
 *
 * <p>Exactly the three the PRD and API Reference name. Roles arrive in the token as strings and
 * stay strings on {@link CurrentUser}, because that is the wire contract - this enum exists for
 * the other end, so a rule reads {@code requireRole(user, Role.ADMIN)} and a mistyped role is a
 * compile error rather than a check that silently denies everybody forever.
 */
public enum Role {

	/** An institutional member. The ordinary reader. */
	MEMBER,

	/** Operational access, e.g. rebuilding derived state. */
	ADMIN,

	/** An individual subscriber, who belongs to no institution. */
	SUBSCRIBER
}
