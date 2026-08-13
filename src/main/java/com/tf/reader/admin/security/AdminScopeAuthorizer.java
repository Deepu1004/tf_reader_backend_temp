package com.tf.reader.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.security.TokenClaims;

/**
 * Decides whether the current admin may operate on a given publisher or institution.
 *
 * <p>Intended for {@code @PreAuthorize("@adminScope.canAccessPublisher(#publisherId)")}. Role
 * answers "what kind of admin is this"; this component answers "which tenant may they touch",
 * which is why there is no per-publisher or per-institution role.
 *
 * <p>Every rule fails closed:
 * <ul>
 * <li>no authentication, or not a JWT authentication, denies
 * <li>an unrecognised role denies
 * <li>a missing or blank scope claim denies; it is never read as global access
 * <li>a blank target denies
 * <li>matching is exact equality, never prefix or substring
 * <li>a role may only be checked against its own dimension, so an institution admin is denied by
 * {@link #canAccessPublisher} regardless of claims
 * </ul>
 */
@Component("adminScope")
public class AdminScopeAuthorizer {

	public boolean canAccessPublisher(String publisherId) {
		return canAccess(AdminRole.PUBLISHER_ADMIN, TokenClaims.SCOPE_PUBLISHER_ID, publisherId);
	}

	public boolean canAccessInstitution(String institutionId) {
		return canAccess(AdminRole.INSTITUTION_ADMIN, TokenClaims.SCOPE_INSTITUTION_ID, institutionId);
	}

	private boolean canAccess(AdminRole scopedRole, String scopeClaim, String targetId) {
		// "May I act on nothing in particular?" is not a question this can answer safely, so a blank
		// target denies for every role. That also stops a null argument from silently passing a
		// super admin through.
		if (isBlank(targetId)) {
			return false;
		}

		Jwt jwt = currentAdminJwt();
		if (jwt == null) {
			return false;
		}

		AdminRole role = AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
		if (role == null) {
			return false;
		}
		if (role == AdminRole.SUPER_ADMIN) {
			return true;
		}
		if (role != scopedRole) {
			return false;
		}

		String scope = jwt.getClaimAsString(scopeClaim);
		// Exact equality only. Prefix or substring matching would let publisher-1 reach
		// publisher-10.
		return !isBlank(scope) && scope.equals(targetId);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static Jwt currentAdminJwt() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		return (authentication.getPrincipal() instanceof Jwt jwt) ? jwt : null;
	}

}
