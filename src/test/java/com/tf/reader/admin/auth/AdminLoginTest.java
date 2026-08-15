package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.common.security.JwtConfig;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

class AdminLoginTest extends AbstractAdminAuthIntegrationTest {

	@Autowired
	@Qualifier(JwtConfig.ADMIN_ACCESS_TOKEN_DECODER)
	private JwtDecoder adminAccessTokenDecoder;

	@Test
	void issuesTokensForCorrectCredentials() throws Exception {
		saveAdmin("active@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = performLogin("active@tandf.example", PASSWORD);
		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		AdminLoginResponse tokens = readLogin(result);
		assertThat(tokens.accessToken()).isNotBlank();
		assertThat(tokens.refreshToken()).isNotBlank();
		assertThat(tokens.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());
		assertThat(tokens.refreshExpiresIn()).isEqualTo(Duration.ofHours(12).toSeconds());
	}

	@Test
	void opensExactlyOneServerSideSessionPerLogin() throws Exception {
		AdminUser admin = saveAdmin("session@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		AdminLoginResponse tokens = loginSuccessfully("session@tandf.example");

		assertThat(this.adminSessionRepository.findAll())
				.singleElement()
				.satisfies(session -> {
					assertThat(session.getAdminUserId()).isEqualTo(admin.getId());
					assertThat(session.getRevokedAt()).isNull();
					assertThat(session.getId()).startsWith("sess_");
					assertThat(session.getRefreshTokenHash()).isNotBlank()
							.isNotEqualTo(tokens.refreshToken());
				});
	}

	@Test
	void recordsTheLoginTimestamp() throws Exception {
		AdminUser admin = saveAdmin("stamp@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		assertThat(admin.getLastLoginAt()).isNull();

		loginSuccessfully("stamp@tandf.example");

		assertThat(this.adminUserRepository.findById(admin.getId()).orElseThrow().getLastLoginAt())
				.isNotNull()
				.isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(Duration.ofMinutes(1)));
	}

	@Test
	void rejectsAWrongPassword() throws Exception {
		saveAdmin("wrongpw@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = performLogin("wrongpw@tandf.example", "not-the-password");

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(this.adminSessionRepository.count()).isZero();
	}

	@Test
	void rejectsAnUnknownEmail() throws Exception {
		MvcResult result = performLogin("nobody@tandf.example", PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(this.adminSessionRepository.count()).isZero();
	}

	@Test
	void rejectsASuspendedAdmin() throws Exception {
		saveAdmin("suspended@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.SUSPENDED);

		MvcResult result = performLogin("suspended@tandf.example", PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(this.adminSessionRepository.count()).isZero();
	}

	@Test
	void rejectsADisabledAdmin() throws Exception {
		saveAdmin("disabled@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.DISABLED);

		MvcResult result = performLogin("disabled@tandf.example", PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(this.adminSessionRepository.count()).isZero();
	}

	/**
	 * The three ways to fail must be indistinguishable, otherwise the endpoint becomes an account
	 * and status oracle.
	 */
	@Test
	void returnsAnIdenticalResponseForUnknownWrongPasswordAndSuspended() throws Exception {
		saveAdmin("known@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		saveAdmin("locked@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.SUSPENDED);

		String unknownEmail = withoutVaryingFields(bodyOf(performLogin("ghost@tandf.example", PASSWORD)));
		String wrongPassword = withoutVaryingFields(bodyOf(performLogin("known@tandf.example", "wrong")));
		String suspended = withoutVaryingFields(bodyOf(performLogin("locked@tandf.example", PASSWORD)));

		// Only the clock and the trace id may differ; nothing else may hint at which case occurred.
		assertThat(unknownEmail).isEqualTo(wrongPassword).isEqualTo(suspended);
		assertThat(unknownEmail).doesNotContainIgnoringCase("suspend", "disabl", "exist", "unknown", "password");
	}

	@Test
	void verifiesThePasswordWithBcryptRatherThanComparingLiterally() throws Exception {
		AdminUser admin = saveAdmin("bcrypt@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		assertThat(admin.getPasswordHash()).startsWith("$2");
		assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
		assertThat(this.passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();

		// The stored value itself must not be accepted as if it were the password.
		assertThat(performLogin("bcrypt@tandf.example", admin.getPasswordHash()).getResponse().getStatus())
				.isEqualTo(401);
		assertThat(performLogin("bcrypt@tandf.example", PASSWORD).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void neverReturnsThePasswordHash() throws Exception {
		AdminUser admin = saveAdmin("nohash@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		AdminLoginResponse tokens = loginSuccessfully("nohash@tandf.example");
		String profileBody = bodyOf(callMe(tokens.accessToken()));

		assertThat(profileBody).doesNotContain("passwordHash").doesNotContain(admin.getPasswordHash());
	}

	@Test
	void issuesAnAccessTokenWithTheExpectedIssuerAudienceAndExpiry() throws Exception {
		AdminUser admin = saveAdmin("claims@tandf.example", AdminRole.PUBLISHER_ADMIN, AdminStatus.ACTIVE,
				"publisher-1", null);

		AdminLoginResponse tokens = loginSuccessfully("claims@tandf.example");
		Jwt accessToken = this.adminAccessTokenDecoder.decode(tokens.accessToken());

		assertThat(accessToken.getClaimAsString("iss")).isEqualTo("tf-reader");
		assertThat(accessToken.getAudience()).containsExactly(TokenAudience.ADMIN);
		assertThat(accessToken.getSubject()).isEqualTo(admin.getId());
		assertThat(accessToken.getId()).isNotBlank();
		assertThat(accessToken.getClaimAsString(TokenClaims.TOKEN_USE)).isEqualTo(TokenClaims.USE_ACCESS);
		assertThat(accessToken.getClaimAsString(TokenClaims.ROLE)).isEqualTo("PUBLISHER_ADMIN");
		assertThat(accessToken.getClaimAsString(TokenClaims.SESSION_ID)).isNotBlank();
		assertThat(accessToken.getIssuedAt()).isNotNull();
		assertThat(accessToken.getExpiresAt())
				.isCloseTo(accessToken.getIssuedAt().plus(Duration.ofMinutes(15)),
						org.assertj.core.api.Assertions.within(Duration.ofSeconds(5)));
	}

	@Test
	void writesOnlyTheScopeClaimTheAdminActuallyHas() throws Exception {
		saveAdmin("pubscope@tandf.example", AdminRole.PUBLISHER_ADMIN, AdminStatus.ACTIVE, "publisher-7", null);
		saveAdmin("instscope@tandf.example", AdminRole.INSTITUTION_ADMIN, AdminStatus.ACTIVE, null,
				"institution-9");
		saveAdmin("superscope@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		Jwt publisherToken = decodeAccessTokenFor("pubscope@tandf.example");
		assertThat(publisherToken.getClaimAsString(TokenClaims.SCOPE_PUBLISHER_ID)).isEqualTo("publisher-7");
		assertThat(publisherToken.hasClaim(TokenClaims.SCOPE_INSTITUTION_ID)).isFalse();

		Jwt institutionToken = decodeAccessTokenFor("instscope@tandf.example");
		assertThat(institutionToken.getClaimAsString(TokenClaims.SCOPE_INSTITUTION_ID)).isEqualTo("institution-9");
		assertThat(institutionToken.hasClaim(TokenClaims.SCOPE_PUBLISHER_ID)).isFalse();

		// A super admin is global by role, never by carrying a scope claim.
		Jwt superToken = decodeAccessTokenFor("superscope@tandf.example");
		assertThat(superToken.hasClaim(TokenClaims.SCOPE_PUBLISHER_ID)).isFalse();
		assertThat(superToken.hasClaim(TokenClaims.SCOPE_INSTITUTION_ID)).isFalse();
	}

	@Test
	void returnsTheAuthenticatedAdminFromMe() throws Exception {
		AdminUser admin = saveAdmin("me@tandf.example", AdminRole.INSTITUTION_ADMIN, AdminStatus.ACTIVE, null,
				"institution-3");

		AdminLoginResponse tokens = loginSuccessfully("me@tandf.example");
		MvcResult result = callMe(tokens.accessToken());

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(bodyOf(result))
				.contains(admin.getId())
				.contains("me@tandf.example")
				.contains("INSTITUTION_ADMIN")
				.contains("institution-3")
				.contains("ACTIVE");

		// The contract names the scope fields with a scope prefix; the unprefixed names must be gone.
		assertThat(bodyOf(result))
				.contains("\"scopeInstitutionId\":\"institution-3\"")
				.doesNotContain("\"institutionId\"")
				.doesNotContain("\"publisherId\"");
	}

	@Test
	void rejectsMeWithoutAToken() throws Exception {
		assertThat(this.mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ME_PATH))
				.andReturn().getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAMalformedLoginBody() throws Exception {
		saveAdmin("validation@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		assertThat(performLogin("not-an-email", PASSWORD).getResponse().getStatus()).isEqualTo(400);
		assertThat(performLogin("validation@tandf.example", "").getResponse().getStatus()).isEqualTo(400);
	}

	private Jwt decodeAccessTokenFor(String email) throws Exception {
		return this.adminAccessTokenDecoder.decode(loginSuccessfully(email).accessToken());
	}

	private static String bodyOf(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString();
	}

	/**
	 * Drops the two fields that legitimately vary between otherwise identical responses: the clock, and
	 * the trace id, which is fresh per request by design so a caller can quote one when reporting a
	 * problem.
	 */
	private static String withoutVaryingFields(String errorJson) {
		return errorJson
				.replaceAll(",?\"timestamp\":\"[^\"]*\"", "")
				.replaceAll(",?\"traceId\":\"[^\"]*\"", "");
	}

}
