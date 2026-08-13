package com.tf.reader.admin.security;

import com.tf.reader.admin.entity.AdminRole;

/**
 * Single place that turns an untrusted {@code role} claim into an {@link AdminRole}.
 *
 * <p>Kept in one helper so the decoder validator, the authentication converter and the scope
 * authorizer cannot drift apart in how they interpret the claim.
 */
public final class AdminRoles {

	private AdminRoles() {
	}

	/** @return the matching role, or null when the value is absent or not a known role. */
	public static AdminRole parse(String claimValue) {
		if (claimValue == null || claimValue.isBlank()) {
			return null;
		}
		try {
			return AdminRole.valueOf(claimValue);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	public static boolean isValid(String claimValue) {
		return parse(claimValue) != null;
	}

}
