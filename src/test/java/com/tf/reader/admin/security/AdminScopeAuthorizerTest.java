package com.tf.reader.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

/**
 * The scope decision, isolated from HTTP. Every case that should deny is asserted explicitly,
 * because a silent "true" here is a tenant isolation breach.
 */
class AdminScopeAuthorizerTest {

	private final AdminScopeAuthorizer authorizer = new AdminScopeAuthorizer();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void grantsASuperAdminAccessToAnyPublisherOrInstitution() {
		authenticateAs(AdminRole.SUPER_ADMIN, null, null);

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isTrue();
		assertThat(this.authorizer.canAccessPublisher("any-other-publisher")).isTrue();
		assertThat(this.authorizer.canAccessInstitution("institution-1")).isTrue();
		assertThat(this.authorizer.canAccessInstitution("any-other-institution")).isTrue();
	}

	@Test
	void grantsAPublisherAdminAccessToItsOwnPublisher() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "publisher-1", null);

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isTrue();
	}

	@Test
	void deniesAPublisherAdminAccessToAnotherPublisher() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "publisher-1", null);

		assertThat(this.authorizer.canAccessPublisher("publisher-2")).isFalse();
	}

	@Test
	void grantsAnInstitutionAdminAccessToItsOwnInstitution() {
		authenticateAs(AdminRole.INSTITUTION_ADMIN, null, "institution-1");

		assertThat(this.authorizer.canAccessInstitution("institution-1")).isTrue();
	}

	@Test
	void deniesAnInstitutionAdminAccessToAnotherInstitution() {
		authenticateAs(AdminRole.INSTITUTION_ADMIN, null, "institution-1");

		assertThat(this.authorizer.canAccessInstitution("institution-2")).isFalse();
	}

	/** A missing scope means no access. It must never be read as "unscoped, therefore global". */
	@Test
	void deniesAScopedAdminThatCarriesNoScopeClaim() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, null, null);
		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();

		authenticateAs(AdminRole.INSTITUTION_ADMIN, null, null);
		assertThat(this.authorizer.canAccessInstitution("institution-1")).isFalse();
	}

	@Test
	void deniesAScopedAdminWhoseScopeClaimIsBlank() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "   ", null);

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("   ")).isFalse();
	}

	/** Each role may only be checked against its own dimension. */
	@Test
	void deniesAcrossScopeDimensions() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "publisher-1", null);
		assertThat(this.authorizer.canAccessInstitution("publisher-1")).isFalse();
		assertThat(this.authorizer.canAccessInstitution("institution-1")).isFalse();

		authenticateAs(AdminRole.INSTITUTION_ADMIN, null, "institution-1");
		assertThat(this.authorizer.canAccessPublisher("institution-1")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();
	}

	/**
	 * Guards against prefix, substring and case-insensitive matching. Any of those would let one
	 * tenant reach another whose id merely resembles its own.
	 */
	@Test
	void matchesScopesExactlyAndNeverByResemblance() {
		authenticateAs(AdminRole.PUBLISHER_ADMIN, "publisher-1", null);

		assertThat(this.authorizer.canAccessPublisher("publisher-10")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("publisher-1x")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("publisher-")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("publisher")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("PUBLISHER-1")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("Publisher-1")).isFalse();
		assertThat(this.authorizer.canAccessPublisher(" publisher-1")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("publisher-1 ")).isFalse();
		assertThat(this.authorizer.canAccessPublisher("xpublisher-1")).isFalse();

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isTrue();
	}

	@Test
	void deniesABlankOrMissingTargetForEveryRole() {
		for (AdminRole role : AdminRole.values()) {
			authenticateAs(role, "publisher-1", "institution-1");

			assertThat(this.authorizer.canAccessPublisher(null)).as("null publisher for %s", role).isFalse();
			assertThat(this.authorizer.canAccessPublisher("")).as("empty publisher for %s", role).isFalse();
			assertThat(this.authorizer.canAccessPublisher("  ")).as("blank publisher for %s", role).isFalse();
			assertThat(this.authorizer.canAccessInstitution(null)).as("null institution for %s", role).isFalse();
			assertThat(this.authorizer.canAccessInstitution("")).as("empty institution for %s", role).isFalse();
		}
	}

	@Test
	void deniesWhenThereIsNoAuthentication() {
		SecurityContextHolder.clearContext();

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();
		assertThat(this.authorizer.canAccessInstitution("institution-1")).isFalse();
	}

	@Test
	void deniesWhenTheAuthenticationIsNotAJwt() {
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated("someone", "n/a",
						List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();
	}

	@Test
	void deniesWhenTheAuthenticationIsNotAuthenticated() {
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(jwtFor(AdminRole.SUPER_ADMIN, null, null), "n/a"));

		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();
	}

	/** An authority alone must not grant scope; the role claim is what is consulted. */
	@Test
	void deniesWhenTheRoleClaimIsMissingOrUnknownEvenWithASuperAdminAuthority() {
		Jwt withoutRole = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject("admin-1")
				.audience(List.of(TokenAudience.ADMIN))
				.claim(TokenClaims.TOKEN_USE, TokenClaims.USE_ACCESS)
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
				.build();
		authenticate(withoutRole);
		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();

		Jwt unknownRole = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.subject("admin-1")
				.audience(List.of(TokenAudience.ADMIN))
				.claim(TokenClaims.TOKEN_USE, TokenClaims.USE_ACCESS)
				.claim(TokenClaims.ROLE, "ROOT")
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
				.build();
		authenticate(unknownRole);
		assertThat(this.authorizer.canAccessPublisher("publisher-1")).isFalse();
	}

	private void authenticateAs(AdminRole role, String publisherScope, String institutionScope) {
		authenticate(jwtFor(role, publisherScope, institutionScope));
	}

	private void authenticate(Jwt jwt) {
		SecurityContextHolder.getContext()
				.setAuthentication(new AdminJwtAuthenticationConverter().convert(jwt));
	}

	private static Jwt jwtFor(AdminRole role, String publisherScope, String institutionScope) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
				.header("alg", "HS256")
				.issuer("tf-reader")
				.subject("admin-1")
				.audience(List.of(TokenAudience.ADMIN))
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
				.claims(claims -> claims.putAll(Map.of(
						TokenClaims.TOKEN_USE, TokenClaims.USE_ACCESS,
						TokenClaims.SESSION_ID, "session-1",
						TokenClaims.ROLE, role.name())));

		if (publisherScope != null) {
			builder.claim(TokenClaims.SCOPE_PUBLISHER_ID, publisherScope);
		}
		if (institutionScope != null) {
			builder.claim(TokenClaims.SCOPE_INSTITUTION_ID, institutionScope);
		}
		return builder.build();
	}

}
