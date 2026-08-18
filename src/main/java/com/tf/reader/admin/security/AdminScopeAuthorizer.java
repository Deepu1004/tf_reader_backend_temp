package com.tf.reader.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.security.TokenClaims;

/**
 * Decides whether the current admin may operate on a given publisher or institution, for use as
 * {@code @PreAuthorize("@adminScope.canAccessPublisher(#publisherId)")}.
 *
 * <p>Every rule fails closed: missing authentication, an unrecognised role, a blank scope claim and a
 * blank target all deny, and a role is only ever checked against its own dimension.
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
		// A blank target denies for every role, so a null argument cannot pass a super admin through.
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
		// Exact equality only: prefix matching would let publisher-1 reach publisher-10.
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
