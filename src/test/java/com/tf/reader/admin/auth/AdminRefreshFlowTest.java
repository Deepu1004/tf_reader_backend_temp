package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.TokenResponse;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.service.AdminSessionService;
import com.tf.reader.common.security.JwtConfig;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

class AdminRefreshFlowTest extends AbstractAdminAuthIntegrationTest {

	@Autowired
	@Qualifier(JwtConfig.ADMIN_ACCESS_TOKEN_DECODER)
	private JwtDecoder adminAccessTokenDecoder;

	@Autowired
	@Qualifier(JwtConfig.REFRESH_TOKEN_DECODER)
	private JwtDecoder refreshTokenDecoder;

	@Test
	void issuesARefreshTokenWithTheExpectedClaims() throws Exception {
		AdminUser admin = saveAdmin("rclaims@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		TokenResponse tokens = loginSuccessfully("rclaims@tandf.example");
		Jwt refreshToken = this.refreshTokenDecoder.decode(tokens.refreshToken());

		assertThat(refreshToken.getClaimAsString("iss")).isEqualTo("tf-reader");
		assertThat(refreshToken.getAudience()).containsExactly(TokenAudience.REFRESH);
		assertThat(refreshToken.getSubject()).isEqualTo(admin.getId());
		assertThat(refreshToken.getId()).isNotBlank();
		assertThat(refreshToken.getIssuedAt()).isNotNull();
		assertThat(refreshToken.getExpiresAt()).isAfter(Instant.now().plus(13, ChronoUnit.DAYS));
		assertThat(refreshToken.getClaimAsString(TokenClaims.TOKEN_USE)).isEqualTo(TokenClaims.USE_REFRESH);
		assertThat(refreshToken.getClaimAsString(TokenClaims.SESSION_ID)).isNotBlank();

		// A refresh token authorizes nothing on its own, so it carries no role or scope.
		assertThat(refreshToken.hasClaim(TokenClaims.ROLE)).isFalse();
		assertThat(refreshToken.hasClaim(TokenClaims.SCOPE_PUBLISHER_ID)).isFalse();
	}

	@Test
	void exchangesAValidRefreshTokenForAWorkingNewAccessToken() throws Exception {
		saveAdmin("exchange@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse original = loginSuccessfully("exchange@tandf.example");

		MvcResult result = callRefresh(original.refreshToken());
		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		TokenResponse refreshed = readTokens(result);
		assertThat(refreshed.accessToken()).isNotBlank().isNotEqualTo(original.accessToken());
		assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(original.refreshToken());
		assertThat(refreshed.tokenType()).isEqualTo("Bearer");

		// The new access token must actually work.
		assertThat(callMe(refreshed.accessToken()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void keepsTheSameSessionAcrossRotation() throws Exception {
		saveAdmin("samesession@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse original = loginSuccessfully("samesession@tandf.example");
		String originalSessionId = sessionIdOf(original.accessToken());

		TokenResponse refreshed = readTokens(callRefresh(original.refreshToken()));

		assertThat(sessionIdOf(refreshed.accessToken())).isEqualTo(originalSessionId);
		assertThat(this.adminSessionRepository.count()).isEqualTo(1);
	}

	@Test
	void rotationInvalidatesThePreviousRefreshToken() throws Exception {
		saveAdmin("rotate@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse original = loginSuccessfully("rotate@tandf.example");

		TokenResponse refreshed = readTokens(callRefresh(original.refreshToken()));
		assertThat(refreshed.refreshToken()).isNotEqualTo(original.refreshToken());

		// The superseded token is dead on arrival.
		assertThat(callRefresh(original.refreshToken()).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * Replaying a superseded token is the signature of a stolen refresh token, so the entire session
	 * is revoked rather than only the one request being refused.
	 */
	@Test
	void replayingASupersededRefreshTokenRevokesTheWholeSession() throws Exception {
		saveAdmin("replay@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse original = loginSuccessfully("replay@tandf.example");
		TokenResponse refreshed = readTokens(callRefresh(original.refreshToken()));

		assertThat(callRefresh(original.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		AdminSession session = onlySession();
		assertThat(session.getRevokedAt()).isNotNull();
		assertThat(session.getRevokedReason()).isEqualTo(AdminSessionService.REASON_TOKEN_REUSE);

		// The legitimate holder is locked out too, which is the intended response to a suspected theft.
		assertThat(callRefresh(refreshed.refreshToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(refreshed.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsAnExpiredRefreshToken() throws Exception {
		AdminUser admin = saveAdmin("rexpired@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		loginSuccessfully("rexpired@tandf.example");
		String sessionId = onlySession().getId();

		Instant expiredAt = TestTokenFactory.wellInThePast();
		String expiredRefresh = this.tokens.sign(this.tokens.refreshClaims(admin.getId(), sessionId)
				.issuedAt(expiredAt.minus(1, ChronoUnit.DAYS))
				.expiresAt(expiredAt)
				.build());

		assertThat(callRefresh(expiredRefresh).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsARefreshTokenWithTheWrongAudience() throws Exception {
		AdminUser admin = saveAdmin("raud@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		loginSuccessfully("raud@tandf.example");
		String sessionId = onlySession().getId();

		String adminAudience = this.tokens.sign(this.tokens.refreshClaims(admin.getId(), sessionId)
				.audience(java.util.List.of(TokenAudience.ADMIN))
				.build());
		String appAudience = this.tokens.sign(this.tokens.refreshClaims(admin.getId(), sessionId)
				.audience(java.util.List.of(TokenAudience.APP))
				.build());

		assertThat(callRefresh(adminAudience).getResponse().getStatus()).isEqualTo(401);
		assertThat(callRefresh(appAudience).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsARefreshTokenSignedWithAnotherKeyOrIssuedByAnother() throws Exception {
		AdminUser admin = saveAdmin("rforge@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		loginSuccessfully("rforge@tandf.example");
		String sessionId = onlySession().getId();

		String forged = this.tokens
				.signWithForeignKey(this.tokens.refreshClaims(admin.getId(), sessionId).build());
		String wrongIssuer = this.tokens.sign(this.tokens.refreshClaims(admin.getId(), sessionId)
				.issuer("https://evil.example")
				.build());

		assertThat(callRefresh(forged).getResponse().getStatus()).isEqualTo(401);
		assertThat(callRefresh(wrongIssuer).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * A correctly signed refresh token for a session that was never created must not work. This is
	 * the check that makes the server-side state load bearing rather than decorative.
	 */
	@Test
	void rejectsARefreshTokenWithNoServerSideSession() throws Exception {
		AdminUser admin = saveAdmin("nosession@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		String orphan = this.tokens.sign(this.tokens.refreshClaims(admin.getId(), "never-created").build());

		assertThat(callRefresh(orphan).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void refusesToRefreshForAnAdminSuspendedAfterLogin() throws Exception {
		AdminUser admin = saveAdmin("suspendlater@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("suspendlater@tandf.example");

		admin.setStatus(AdminStatus.SUSPENDED);
		this.adminUserRepository.save(admin);

		assertThat(callRefresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		// The session dies with the refusal, so the still-valid access token stops working too.
		assertThat(onlySession().getRevokedAt()).isNotNull();
		assertThat(callMe(tokens.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void refusesToRefreshForADeletedAdmin() throws Exception {
		saveAdmin("deleted@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("deleted@tandf.example");

		this.adminUserRepository.deleteAll();

		assertThat(callRefresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void picksUpARoleChangeOnTheNextRefresh() throws Exception {
		AdminUser admin = saveAdmin("rolechange@tandf.example", AdminRole.PUBLISHER_ADMIN, AdminStatus.ACTIVE,
				"publisher-1", null);
		TokenResponse original = loginSuccessfully("rolechange@tandf.example");
		assertThat(roleOf(original.accessToken())).isEqualTo("PUBLISHER_ADMIN");

		admin.setRole(AdminRole.SUPER_ADMIN);
		admin.setPublisherId(null);
		this.adminUserRepository.save(admin);

		TokenResponse refreshed = readTokens(callRefresh(original.refreshToken()));

		assertThat(roleOf(refreshed.accessToken())).isEqualTo("SUPER_ADMIN");
		assertThat(this.adminAccessTokenDecoder.decode(refreshed.accessToken())
				.hasClaim(TokenClaims.SCOPE_PUBLISHER_ID)).isFalse();
	}

	@Test
	void rejectsAnEmptyOrMissingRefreshTokenField() throws Exception {
		assertThat(callRefresh("").getResponse().getStatus()).isEqualTo(400);
	}

	private String sessionIdOf(String accessToken) {
		return this.adminAccessTokenDecoder.decode(accessToken).getClaimAsString(TokenClaims.SESSION_ID);
	}

	private String roleOf(String accessToken) {
		return this.adminAccessTokenDecoder.decode(accessToken).getClaimAsString(TokenClaims.ROLE);
	}

}
