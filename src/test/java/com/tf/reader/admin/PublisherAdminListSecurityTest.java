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
 * Tests the role-check logic directly on the authorizer, without a servlet.
 *
 * <p>
 * This is no longer the guard on the publisher list. That endpoint used to require
 * {@code requireSuperAdmin()}; it now admits all three admin roles and pins a
 * {@code PUBLISHER_ADMIN} to their own publisher instead, which is covered by
 * {@code PublisherAdminServiceTest.publisherAdminSeesOnlyTheirOwnPublisher}. What is left here is
 * still worth keeping - {@code isSuperAdmin()} guards other operations - but a green run here says
 * nothing about who may list publishers.
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
