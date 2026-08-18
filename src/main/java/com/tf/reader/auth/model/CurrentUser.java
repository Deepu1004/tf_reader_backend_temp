package com.tf.reader.auth.model;

import java.util.List;

/**
 * Who is making <em>this</em> request, read from a JWT the server has already verified.
 *
 * <p>Shaped like {@link TnfUser} because the JWT is a {@code TnfUser} serialised - but a
 * separate type on purpose. {@code TnfUser} is what a sign-in produced; {@code CurrentUser} is
 * the identity of a request in flight. Collapsing them into one class is how a login-time
 * object ends up being passed where a request identity was expected, with nothing to notice it.
 *
 * <p>Carries identity only. No token, no signing key, no SAML assertion, no credential of any
 * kind - a service that needs to know <em>who</em> the caller is should never be handed the
 * means to impersonate them.
 */
public record CurrentUser(
		String userId,
		UserType type,
		String institutionId,
		List<String> roles,
		List<String> collections) {

	public CurrentUser {
		roles = List.copyOf(roles);
		collections = List.copyOf(collections);
	}

	/**
	 * True for a user signed in through an institution. An individual subscriber has no
	 * institution, so {@link #institutionId()} is null and every institution-scoped rule must
	 * treat them as belonging to none rather than to a default one.
	 */
	public boolean belongsToAnInstitution() {
		return institutionId != null && !institutionId.isBlank();
	}
}
