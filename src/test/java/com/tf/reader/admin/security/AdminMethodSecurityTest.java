package com.tf.reader.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

/**
 * Proves the authorization mechanism is actually wired: that {@code @EnableMethodSecurity} is on,
 * that the {@code @adminScope} bean name resolves in SpEL, and that a denial raises
 * {@link AccessDeniedException} rather than quietly returning.
 *
 * <p>{@link ScopedOperations} exists only for this test. It is a stand-in for the scoped business
 * endpoints that later tasks will add, not production behaviour.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, AdminMethodSecurityTest.ScopedOperationsConfiguration.class })
class AdminMethodSecurityTest {

	@Autowired
	private ScopedOperations scopedOperations;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void allowsAPublisherAdminWithinItsOwnPublisher() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "publisher-1", null);

		assertThat(this.scopedOperations.updatePublisher("publisher-1")).isEqualTo("updated publisher-1");
	}

	@Test
	void deniesAPublisherAdminOutsideItsPublisher() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "publisher-1", null);

		assertThatThrownBy(() -> this.scopedOperations.updatePublisher("publisher-2"))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void allowsASuperAdminEverywhere() {
		authenticateAs(AdminRole.SUPER_ADMIN, null, null);

		assertThat(this.scopedOperations.updatePublisher("publisher-2")).isEqualTo("updated publisher-2");
		assertThat(this.scopedOperations.updateInstitution("institution-9"))
				.isEqualTo("updated institution-9");
	}

	@Test
	void deniesAnInstitutionAdminOnAPublisherOperation() {
		authenticateAs(AdminRole.INSTITUTION_ADMIN, null, "institution-1");

		assertThatThrownBy(() -> this.scopedOperations.updatePublisher("institution-1"))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void deniesAScopedAdminWithNoScopeClaim() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, null, null);

		assertThatThrownBy(() -> this.scopedOperations.updatePublisher("publisher-1"))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void deniesWhenThereIsNoAuthentication() {
		SecurityContextHolder.clearContext();

		assertThatThrownBy(() -> this.scopedOperations.updatePublisher("publisher-1"))
				.isInstanceOf(AccessDeniedException.class);
	}

	private static void authenticateAs(AdminRole role, String publisherScope, String institutionScope) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.issuer("tf-reader")
				.subject("admin-1")
				.audience(List.of(TokenAudience.ADMIN))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
				.claim(TokenClaims.TOKEN_USE, TokenClaims.USE_ACCESS)
				.claim(TokenClaims.SESSION_ID, "session-1")
				.claim(TokenClaims.ROLE, role.name());

		if (publisherScope != null) {
			builder.claim(TokenClaims.SCOPE_PUBLISHER_ID, publisherScope);
		}
		if (institutionScope != null) {
			builder.claim(TokenClaims.SCOPE_INSTITUTION_ID, institutionScope);
		}

		SecurityContextHolder.getContext()
				.setAuthentication(new AdminJwtAuthenticationConverter().convert(builder.build()));
	}

	@TestConfiguration
	static class ScopedOperationsConfiguration {

		@org.springframework.context.annotation.Bean
		ScopedOperations scopedOperations() {
			return new ScopedOperations();
		}

	}

	/** Test-only stand-in for a scope-guarded service method. */
	@Service
	static class ScopedOperations {

		@PreAuthorize("@adminScope.canAccessPublisher(#publisherId)")
		String updatePublisher(String publisherId) {
			return "updated " + publisherId;
		}

		@PreAuthorize("@adminScope.canAccessInstitution(#institutionId)")
		String updateInstitution(String institutionId) {
			return "updated " + institutionId;
		}

	}

}
