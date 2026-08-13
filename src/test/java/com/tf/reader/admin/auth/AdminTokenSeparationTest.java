package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.TokenResponse;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Every way a token can be wrong must end in 401, and no token minted for one audience may work
 * against another.
 */
class AdminTokenSeparationTest extends AbstractAdminAuthIntegrationTest {

	/** Any path under the app chain: the token is rejected while decoding, before routing. */
	private static final String APP_PATH = "/api/app/v1/catalogue";

	@Test
	void rejectsAnAppTokenOnTheAdminApi() throws Exception {
		String appToken = this.tokens.sign(this.tokens.appAccessClaims("reader-1").build());

		assertThat(callMe(appToken).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnAdminTokenOnTheAppApi() throws Exception {
		AdminUser admin = saveAdmin("cross@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("cross@tandf.example");

		assertThat(callApp(tokens.accessToken())).isEqualTo(401);
		assertThat(admin.getId()).isNotBlank();
	}

	@Test
	void rejectsARefreshTokenOnTheAdminApi() throws Exception {
		saveAdmin("refuse@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("refuse@tandf.example");

		assertThat(callMe(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnAccessTokenAtTheRefreshEndpoint() throws Exception {
		saveAdmin("noswap@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("noswap@tandf.example");

		// An access token can never be exchanged, so letting one expire is not a route to renewal.
		assertThat(callRefresh(tokens.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAMalformedToken() throws Exception {
		assertThat(callMe("this-is-not-a-jwt").getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe("aaa.bbb.ccc").getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe("").getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnExpiredAccessToken() throws Exception {
		AdminUser admin = saveAdmin("expired@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		Instant expiredAt = TestTokenFactory.wellInThePast();
		String expiredToken = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
						.issuedAt(expiredAt.minus(15, ChronoUnit.MINUTES))
						.expiresAt(expiredAt)
						.build());

		assertThat(callMe(expiredToken).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAWrongAudienceEvenWhenEverythingElseIsCorrect() throws Exception {
		AdminUser admin = saveAdmin("aud@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		String wrongAudience = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
						.audience(List.of("tf-somewhere-else"))
						.build());

		assertThat(callMe(wrongAudience).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * A token listing several audiences must not be accepted by any of them. Matching is exact, not
	 * "contains", so one permissive token can never bridge two surfaces.
	 */
	@Test
	void rejectsATokenThatClaimsSeveralAudiencesAtOnce() throws Exception {
		AdminUser admin = saveAdmin("multiaud@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		String multiAudience = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
						.audience(List.of(TokenAudience.ADMIN, TokenAudience.APP, TokenAudience.REFRESH))
						.build());

		assertThat(callMe(multiAudience).getResponse().getStatus()).isEqualTo(401);
		assertThat(callApp(multiAudience)).isEqualTo(401);
		assertThat(callRefresh(multiAudience).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAWrongIssuer() throws Exception {
		AdminUser admin = saveAdmin("iss@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		String wrongIssuer = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
						.issuer("https://evil.example")
						.build());

		assertThat(callMe(wrongIssuer).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() throws Exception {
		AdminUser admin = saveAdmin("sig@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		String forged = this.tokens.signWithForeignKey(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN).build());

		assertThat(callMe(forged).getResponse().getStatus()).isEqualTo(401);
	}

	/** {@code token_use} is checked independently of the audience, so neither check stands alone. */
	@Test
	void rejectsAnAdminAudienceTokenMarkedAsARefreshToken() throws Exception {
		AdminUser admin = saveAdmin("use@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		String mislabelled = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
						.claim(TokenClaims.TOKEN_USE, TokenClaims.USE_REFRESH)
						.build());

		assertThat(callMe(mislabelled).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnAdminTokenWithNoRoleOrAnUnknownRole() throws Exception {
		AdminUser admin = saveAdmin("role@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		JwtClaimsSet noRole = this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
				.claims(claims -> claims.remove(TokenClaims.ROLE))
				.build();
		String unknownRole = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), sessionId, AdminRole.SUPER_ADMIN)
						.claim(TokenClaims.ROLE, "ROOT")
						.build());

		assertThat(callMe(this.tokens.sign(noRole)).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(unknownRole).getResponse().getStatus()).isEqualTo(401);
	}

	/** Without a session claim there is nothing to revoke against, so the token cannot be trusted. */
	@Test
	void rejectsAnAdminTokenWithNoSessionOrAnUnknownSession() throws Exception {
		AdminUser admin = saveAdmin("sid@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		String noSession = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), "ignored", AdminRole.SUPER_ADMIN)
						.claims(claims -> claims.remove(TokenClaims.SESSION_ID))
						.build());
		String unknownSession = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), "no-such-session", AdminRole.SUPER_ADMIN).build());

		assertThat(callMe(noSession).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(unknownSession).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void deniesUnmappedPathsRatherThanExposingThem() throws Exception {
		assertThat(this.mockMvc.perform(get("/api/admin/v1/anything")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
		assertThat(this.mockMvc.perform(get("/internal/metrics")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
		assertThat(this.mockMvc.perform(get("/actuator/beans")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
	}

	@Test
	void keepsTheHealthEndpointPublic() throws Exception {
		assertThat(this.mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus())
				.isEqualTo(200);
	}

	/**
	 * Login and refresh parse no bearer token, so a client holding a stale or corrupt access token
	 * can still authenticate instead of being locked out by its own leftover header.
	 */
	@Test
	void letsAClientLogInWhileStillSendingAnUnusableAuthorizationHeader() throws Exception {
		saveAdmin("stale@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = this.mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(LOGIN_PATH)
						.header("Authorization", "Bearer completely-invalid-token")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("""
								{"email": "stale@tandf.example", "password": "%s"}""".formatted(PASSWORD)))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		TokenResponse tokens = readTokens(result);
		MvcResult refreshResult = this.mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(REFRESH_PATH)
						.header("Authorization", "Bearer completely-invalid-token")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken": "%s"}""".formatted(tokens.refreshToken())))
				.andReturn();

		assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);
	}

	/**
	 * Documentation is a dev-profile feature. Outside it, these paths fall to the deny-all chain, so
	 * enabling springdoc cannot widen the production surface.
	 */
	@Test
	void doesNotExposeApiDocumentationOutsideTheDevProfile() throws Exception {
		assertThat(this.mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
		assertThat(this.mockMvc.perform(get("/v3/api-docs/swagger-config")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
		assertThat(this.mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
		assertThat(this.mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus())
				.isEqualTo(401);
	}

	/** Logs in only to obtain a real, active session id to attach to hand-crafted tokens. */
	private String openSessionFor(AdminUser admin) throws Exception {
		loginSuccessfully(admin.getEmail());
		return this.adminSessionRepository.findAll().stream()
				.filter(session -> session.getAdminUserId().equals(admin.getId()))
				.findFirst()
				.orElseThrow()
				.getId();
	}

	private int callApp(String bearerToken) throws Exception {
		MvcResult result = this.mockMvc.perform(get(APP_PATH).header("Authorization", "Bearer " + bearerToken))
				.andReturn();
		return result.getResponse().getStatus();
	}

}
