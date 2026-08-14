package com.tf.reader.auth.model;

import java.util.List;

/**
 * A TnF user, resolved from a SAML identity plus the institution that was chosen.
 *
 * <p>These five fields are exactly the identity claims the API Reference names, and they are
 * what TokenService will later put into the JWT. Nothing else is added: the SAML assertion
 * also carries a display name and given name, and we have no use for either.
 */
public record TnfUser(
		String userId,
		UserType type,
		String institutionId,
		List<String> roles,
		List<String> collections) {

	public TnfUser {
		roles = List.copyOf(roles);
		collections = List.copyOf(collections);
	}
}
