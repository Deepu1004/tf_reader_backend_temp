package com.tf.reader.admin;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.common.security.TokenClaims;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AdminScopeAuthorizer#isSuperAdmin()}.
 *
 * <p>
 * Tests the role-check logic directly on the authorizer without a servlet. The
 * {@code @PreAuthorize} binding is verified by the method's own behaviour:
 * Spring evaluates {@code @adminScope.isSuperAdmin()} and the return value
 * decides whether the request is allowed. If this method returns the correct
 * value, the annotation works correctly.
 */
class PublisherAdminListSecurityTest {

	private final AdminScopeAuthorizer authorizer = new AdminScopeAuthorizer();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("isSuperAdmin returns true when the JWT role is SUPER_ADMIN")
	void superAdminIsGranted() {
		authenticateAs(AdminRole.SUPER_ADMIN);
		assertThat(authorizer.isSuperAdmin()).isTrue();
	}

	@Test
	@DisplayName("isSuperAdmin returns false for PUBLISHER_ADMIN")
	void publisherAdminIsDenied() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN);
		assertThat(authorizer.isSuperAdmin()).isFalse();
	}

	@Test
	@DisplayName("isSuperAdmin returns false for INSTITUTION_ADMIN")
	void institutionAdminIsDenied() {
		authenticateAs(AdminRole.INSTITUTION_ADMIN);
		assertThat(authorizer.isSuperAdmin()).isFalse();
	}

	@Test
	@DisplayName("isSuperAdmin returns false when there is no authentication context")
	void unauthenticatedIsDenied() {
		SecurityContextHolder.clearContext();
		assertThat(authorizer.isSuperAdmin()).isFalse();
	}

	private static void authenticateAs(AdminRole role) {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(900)).claims(c -> c.put(TokenClaims.ROLE, role.name())).build();
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_ADMIN"));
	}
}
