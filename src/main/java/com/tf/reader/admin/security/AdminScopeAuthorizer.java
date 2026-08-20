package com.tf.reader.admin.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.security.TokenClaims;

/**
 * Decides whether the current admin may operate on a given publisher or
 * institution, for use as
 * {@code @PreAuthorize("@adminScope.canAccessPublisher(#publisherId)")}.
 *
 * <p>
 * Every rule fails closed: missing authentication, an unrecognised role, a
 * blank scope claim and a blank target all deny, and a role is only ever
 * checked against its own dimension.
 */
@Component("adminScope")
public class AdminScopeAuthorizer {

	/**
	 * True only for {@code SUPER_ADMIN}. Used to gate cross-publisher list
	 * operations.
	 */
	public boolean isSuperAdmin() {
		Jwt jwt = currentAdminJwt();
		if (jwt == null) {
			return false;
		}
		return AdminRole.SUPER_ADMIN == AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
	}

	public boolean canAccessPublisher(String publisherId) {
		return canAccess(AdminRole.PUBLISHER_ADMIN, TokenClaims.SCOPE_PUBLISHER_ID, publisherId);
	}

	public boolean canAccessInstitution(String institutionId) {
		return canAccess(AdminRole.INSTITUTION_ADMIN, TokenClaims.SCOPE_INSTITUTION_ID, institutionId);
	}

	/** @throws ApiException 403 {@code FORBIDDEN_ROLE} unless the caller is a super admin */
	public void requireSuperAdmin() {
		if (!isSuperAdmin()) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "This operation requires SUPER_ADMIN.");
		}
	}

	/** The subject of the current admin's token, for an audit trail entry. Null when unauthenticated. */
	public String currentAdminId() {
		Jwt jwt = currentAdminJwt();
		return jwt == null ? null : jwt.getSubject();
	}

	/** The current admin's role, or null when unauthenticated or the claim is not a known role. */
	public AdminRole currentRole() {
		Jwt jwt = currentAdminJwt();
		return jwt == null ? null : AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
	}

	/**
	 * Null means unfiltered (a super admin); otherwise the one institution id a list endpoint
	 * should restrict to. For a list, where there is no already-known target id for
	 * {@link #canAccessInstitution} to check against, so any other role or a missing scope claim
	 * narrows to a sentinel that matches nothing rather than to everything.
	 */
	public String currentInstitutionScope() {
		return currentScope(AdminRole.INSTITUTION_ADMIN, TokenClaims.SCOPE_INSTITUTION_ID, "no-institution-claim");
	}

	/** Same as {@link #currentInstitutionScope()}, scoped to a publisher admin instead. */
	public String currentPublisherScope() {
		return currentScope(AdminRole.PUBLISHER_ADMIN, TokenClaims.SCOPE_PUBLISHER_ID, "no-publisher-claim");
	}

	private String currentScope(AdminRole scopedRole, String scopeClaim, String noClaimSentinel) {
		Jwt jwt = currentAdminJwt();
		if (jwt == null) {
			return noClaimSentinel;
		}
		AdminRole role = AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
		if (role == AdminRole.SUPER_ADMIN) {
			return null;
		}
		if (role != scopedRole) {
			return noClaimSentinel;
		}
		String scope = jwt.getClaimAsString(scopeClaim);
		return isBlank(scope) ? noClaimSentinel : scope;
	}

	private boolean canAccess(AdminRole scopedRole, String scopeClaim, String targetId) {
		// A blank target denies for every role, so a null argument cannot pass a super
		// admin through.
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
		// Exact equality only: prefix matching would let publisher-1 reach
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
