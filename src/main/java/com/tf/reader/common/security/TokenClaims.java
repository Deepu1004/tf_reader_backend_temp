package com.tf.reader.common.security;

/** Custom JWT claim names used across admin tokens. */
public final class TokenClaims {

	/** Separates access from refresh independently of the audience. */
	public static final String TOKEN_USE = "token_use";

	/** Server-side session identifier. Stable across refresh-token rotation. */
	public static final String SESSION_ID = "sid";

	public static final String ROLE = "role";

	/** Carries {@code AdminUser.publisherId}. Absent means no access, never global access. */
	public static final String SCOPE_PUBLISHER_ID = "scopePublisherId";

	/** Carries {@code AdminUser.institutionId}. Absent means no access, never global access. */
	public static final String SCOPE_INSTITUTION_ID = "scopeInstitutionId";

	/**
	 * Which physical database this admin's data lives in. Filled in from {@code scopePublisherId}
	 * for now, since there is no separate tenant registry yet. Absent means no publisher scope, same
	 * as {@link #SCOPE_PUBLISHER_ID}.
	 */
	public static final String TENANT_ID = "tenantId";

	public static final String USE_ACCESS = "access";

	private TokenClaims() {
	}

}
