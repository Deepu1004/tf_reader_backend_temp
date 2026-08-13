package com.tf.reader.common.security;

/**
 * Custom JWT claim names used across admin tokens.
 *
 * <p>Scope claims are named after the token concept rather than the persistence field. The mapping
 * from {@link com.tf.reader.admin.entity.AdminUser} is:
 *
 * <ul>
 * <li>{@code AdminUser.publisherId} &rarr; {@link #SCOPE_PUBLISHER_ID}
 * <li>{@code AdminUser.institutionId} &rarr; {@link #SCOPE_INSTITUTION_ID}
 * </ul>
 *
 * <p>Scope claims are omitted entirely when the underlying field is null, and a missing scope claim
 * is always treated as "no access", never as "global access".
 */
public final class TokenClaims {

	/** Distinguishes an access token from a refresh token, independently of the audience. */
	public static final String TOKEN_USE = "token_use";

	/** Server-side session identifier. Stable across refresh-token rotation. */
	public static final String SESSION_ID = "sid";

	/** The admin's {@link com.tf.reader.admin.entity.AdminRole}, as its enum name. */
	public static final String ROLE = "role";

	/** Publisher the admin is scoped to. Absent for admins that are not publisher-scoped. */
	public static final String SCOPE_PUBLISHER_ID = "scopePublisherId";

	/** Institution the admin is scoped to. Absent for admins that are not institution-scoped. */
	public static final String SCOPE_INSTITUTION_ID = "scopeInstitutionId";

	/** Value of {@link #TOKEN_USE} for access tokens. */
	public static final String USE_ACCESS = "access";

	/** Value of {@link #TOKEN_USE} for refresh tokens. */
	public static final String USE_REFRESH = "refresh";

	private TokenClaims() {
	}

}
