package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.AdminLoginResponse;
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

	/**
	 * A real institution-scoped app path from the contract. The endpoint is not written yet, which does
	 * not weaken these tests: a wrong-audience token is rejected while decoding, before routing, so the
	 * absence of a controller is never what produces the 401.
	 */
	private static final String APP_PATH = "/opds/v1/institutions/inst_7f3/catalogue";

	/** Anonymous by contract: open-access browsing works before anyone holds a token. */
	private static final String PUBLIC_OPDS_PATH = "/opds/v1/public/catalogue";

	/** Anonymous by contract: team1's institution picker is how a reader chooses where to sign in. */
	private static final String PUBLIC_INSTITUTIONS_PATH = "/api/v1/institutions";

	@Test
	void rejectsAnAppTokenOnTheAdminApi() throws Exception {
		String appToken = this.tokens.sign(this.tokens.appAccessClaims("reader-1").build());

		assertThat(callMe(appToken).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnAdminTokenOnTheAppApi() throws Exception {
		AdminUser admin = saveAdmin("cross@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("cross@tandf.example");

		assertThat(callApp(tokens.accessToken())).isEqualTo(401);
		assertThat(admin.getId()).isNotBlank();
	}

	/**
	 * A refresh token authorizes nothing. It is opaque, so it is not even a candidate bearer token: the
	 * admin decoder cannot parse it at all.
	 */
	@Test
	void rejectsARefreshTokenOnTheAdminApi() throws Exception {
		saveAdmin("refuse@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("refuse@tandf.example");

		assertThat(callMe(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callApp(tokens.refreshToken())).isEqualTo(401);

		// Presenting it as a bearer token must not revoke or otherwise disturb its session either.
		assertThat(onlySession().getRevokedAt()).isNull();
	}

	/**
	 * The audience refresh tokens used to carry is no longer issued or accepted anywhere, so a JWT
	 * claiming it is just a foreign token.
	 */
	@Test
	void rejectsAJwtClaimingTheRetiredRefreshAudience() throws Exception {
		AdminUser admin = saveAdmin("retired@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String sessionId = openSessionFor(admin);

		String retired = this.tokens
				.sign(this.tokens.retiredRefreshClaims(admin.getId(), sessionId).build());

		assertThat(callMe(retired).getResponse().getStatus()).isEqualTo(401);
		assertThat(callApp(retired)).isEqualTo(401);
		assertThat(callRefresh(retired).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnAccessTokenAtTheRefreshEndpoint() throws Exception {
		saveAdmin("noswap@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("noswap@tandf.example");

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
						.audience(List.of(TokenAudience.ADMIN, TokenAudience.APP,
								TestTokenFactory.RETIRED_REFRESH_AUDIENCE))
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
						.claim(TokenClaims.TOKEN_USE, TestTokenFactory.RETIRED_REFRESH_TOKEN_USE)
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
	 * Login, refresh and logout parse no bearer token, so a client holding a stale or corrupt access
	 * token can still authenticate instead of being locked out by its own leftover header.
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

		AdminLoginResponse tokens = readLogin(result);
		MvcResult refreshResult = this.mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(REFRESH_PATH)
						.header("Authorization", "Bearer completely-invalid-token")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken": "%s"}""".formatted(tokens.refreshToken()))
						.with(csrf()))
				.andReturn();

		assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);

		MvcResult logoutResult = this.mockMvc.perform(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(LOGOUT_PATH)
						.header("Authorization", "Bearer completely-invalid-token")
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken": "%s"}""".formatted(readTokens(refreshResult).refreshToken()))
						.with(csrf()))
				.andReturn();

		assertThat(logoutResult.getResponse().getStatus()).isEqualTo(204);
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

	// The three tests below are about what is ALLOWED. Every other test in this class proves what is
	// refused, and a chain that refused everything would pass all of them — which is exactly how the app
	// chain came to guard a path that did not exist.

	/**
	 * A valid app token has to get past the app chain. The OPDS endpoint is real now: the proof is a
	 * 403, not a 404 - {@code appAccessClaims} mints an {@code INDIVIDUAL} token with no
	 * {@code institutionId}, and an individual can never match an institution-scoped path, so
	 * {@code FORBIDDEN_INSTITUTION_MISMATCH} is the correct terminal answer here, not a stepping
	 * stone to 200. Either way, routing and authentication were reached, which is all this test
	 * ever asserted - a 401 would mean the chain rejected the token, which is what the other tests
	 * in this class check.
	 */
	@Test
	void letsAValidAppTokenPastTheAppChain() throws Exception {
		String appToken = this.tokens.sign(this.tokens.appAccessClaims("reader-1").build());

		assertThat(callApp(appToken)).isEqualTo(403);
	}

	/** Open-access browsing and the institution picker must work with no token at all. */
	@Test
	void leavesThePublicAppPathsOpenToAnAnonymousCaller() throws Exception {
		assertThat(this.mockMvc.perform(get(PUBLIC_OPDS_PATH)).andReturn().getResponse().getStatus())
				.isNotIn(401, 403);
		assertThat(this.mockMvc.perform(get(PUBLIC_INSTITUTIONS_PATH)).andReturn().getResponse().getStatus())
				.isNotIn(401, 403);
	}

	/**
	 * The public chain parses no token, so a stale or foreign one in the header is ignored rather than
	 * failing the request. Without this, a reader whose app token expired could not browse open access.
	 */
	@Test
	void ignoresAnUnusableTokenOnThePublicAppPaths() throws Exception {
		assertThat(this.mockMvc.perform(get(PUBLIC_OPDS_PATH).header("Authorization", "Bearer not-a-jwt"))
				.andReturn().getResponse().getStatus()).isNotIn(401, 403);

		AdminUser admin = saveAdmin("public@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		String adminAudienceToken = this.tokens.sign(
				this.tokens.adminAccessClaims(admin.getId(), "no-such-session", AdminRole.SUPER_ADMIN).build());

		assertThat(this.mockMvc.perform(get(PUBLIC_INSTITUTIONS_PATH)
				.header("Authorization", "Bearer " + adminAudienceToken))
				.andReturn().getResponse().getStatus()).isNotIn(401, 403);
	}

	private int callApp(String bearerToken) throws Exception {
		MvcResult result = this.mockMvc.perform(get(APP_PATH).header("Authorization", "Bearer " + bearerToken))
				.andReturn();
		return result.getResponse().getStatus();
	}

}
