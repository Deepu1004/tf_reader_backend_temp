package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.dto.TokenPair;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.service.AdminSessionService;
import com.tf.reader.common.security.JwtConfig;
import com.tf.reader.common.security.TokenClaims;

class AdminRefreshFlowTest extends AbstractAdminAuthIntegrationTest {

	@Autowired
	@Qualifier(JwtConfig.ADMIN_ACCESS_TOKEN_DECODER)
	private JwtDecoder adminAccessTokenDecoder;

	/**
	 * The refresh token is opaque: 256 bits of randomness, nothing encoded in it, and nothing about it
	 * that a client could parse or forge.
	 */
	@Test
	void issuesAnOpaqueRefreshTokenRatherThanAJwt() throws Exception {
		saveAdmin("ropaque@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		AdminLoginResponse tokens = loginSuccessfully("ropaque@tandf.example");
		String refreshToken = tokens.refreshToken();

		assertThat(refreshToken).isNotBlank().doesNotContain(".");
		assertThat(Base64.getUrlDecoder().decode(refreshToken)).hasSize(32);

		// Nothing recoverable: no admin id, no session id, no expiry.
		assertThat(refreshToken).doesNotContain(onlySession().getId());
		assertThat(new String(Base64.getUrlDecoder().decode(refreshToken)))
				.doesNotContain("tf-reader", "sid", "exp");
	}

	@Test
	void issuesADifferentRefreshTokenEveryTime() throws Exception {
		saveAdmin("rentropy@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		String first = loginSuccessfully("rentropy@tandf.example").refreshToken();
		String second = loginSuccessfully("rentropy@tandf.example").refreshToken();

		assertThat(first).isNotEqualTo(second);
	}

	/** A leaked database dump must not yield usable refresh tokens. */
	@Test
	void storesOnlyAFingerprintOfTheRefreshToken() throws Exception {
		saveAdmin("rhash@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		AdminLoginResponse tokens = loginSuccessfully("rhash@tandf.example");
		AdminSession session = onlySession();

		assertThat(session.getCurrentRefreshTokenHash())
				.isNotEqualTo(tokens.refreshToken())
				.hasSize(64)
				.matches("[0-9a-f]+");
		assertThat(this.objectMapper.writeValueAsString(session)).doesNotContain(tokens.refreshToken());
	}

	/** Twelve hours, so one working day needs one sign in. */
	@Test
	void opensASessionThatLastsTwelveHours() throws Exception {
		saveAdmin("rttl@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		AdminLoginResponse tokens = loginSuccessfully("rttl@tandf.example");
		AdminSession session = onlySession();

		assertThat(session.getExpiresAt())
				.isCloseTo(session.getIssuedAt().plus(Duration.ofHours(12)),
						org.assertj.core.api.Assertions.within(Duration.ofSeconds(5)));
		assertThat(tokens.refreshExpiresIn()).isEqualTo(Duration.ofHours(12).toSeconds());
	}

	@Test
	void exchangesAValidRefreshTokenForAWorkingNewAccessToken() throws Exception {
		saveAdmin("exchange@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse original = loginSuccessfully("exchange@tandf.example");

		MvcResult result = callRefresh(original.refreshToken());
		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		TokenPair refreshed = readTokens(result);
		assertThat(refreshed.accessToken()).isNotBlank().isNotEqualTo(original.accessToken());
		assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(original.refreshToken());
		assertThat(refreshed.expiresIn()).isEqualTo(Duration.ofMinutes(15).toSeconds());

		// The session keeps its original absolute expiry, so refreshing reports the time left on it
		// rather than a fresh twelve hours.
		assertThat(refreshed.refreshExpiresIn())
				.isPositive()
				.isLessThanOrEqualTo(Duration.ofHours(12).toSeconds());

		assertThat(callMe(refreshed.accessToken()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void keepsTheSameSessionAcrossRotation() throws Exception {
		saveAdmin("samesession@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse original = loginSuccessfully("samesession@tandf.example");
		String originalSessionId = sessionIdOf(original.accessToken());

		TokenPair refreshed = readTokens(callRefresh(original.refreshToken()));

		assertThat(sessionIdOf(refreshed.accessToken())).isEqualTo(originalSessionId);
		assertThat(this.adminSessionRepository.count()).isEqualTo(1);
	}

	/** Rotation must never extend the session, however often a client refreshes. */
	@Test
	void neverExtendsTheAbsoluteSessionLifetime() throws Exception {
		saveAdmin("rabsolute@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse original = loginSuccessfully("rabsolute@tandf.example");
		Instant expiryAtLogin = onlySession().getExpiresAt();

		String refreshToken = original.refreshToken();
		for (int i = 0; i < 3; i++) {
			refreshToken = readTokens(callRefresh(refreshToken)).refreshToken();
			assertThat(onlySession().getExpiresAt()).isEqualTo(expiryAtLogin);
		}
	}

	@Test
	void rotationInvalidatesThePreviousRefreshToken() throws Exception {
		saveAdmin("rotate@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse original = loginSuccessfully("rotate@tandf.example");

		TokenPair refreshed = readTokens(callRefresh(original.refreshToken()));
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
		AdminLoginResponse original = loginSuccessfully("replay@tandf.example");
		TokenPair refreshed = readTokens(callRefresh(original.refreshToken()));

		assertThat(callRefresh(original.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		AdminSession session = onlySession();
		assertThat(session.getRevokedAt()).isNotNull();
		assertThat(session.getRevokedReason()).isEqualTo(AdminSessionService.REASON_TOKEN_REUSE);

		// The legitimate holder is locked out too, which is the intended response to a suspected theft.
		assertThat(callRefresh(refreshed.refreshToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(refreshed.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * Reuse is detected however many generations back the replayed token is, not just for the token
	 * immediately preceding the current one.
	 */
	@Test
	void detectsReplayOfATokenSeveralRotationsOld() throws Exception {
		saveAdmin("roldreplay@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse original = loginSuccessfully("roldreplay@tandf.example");

		String second = readTokens(callRefresh(original.refreshToken())).refreshToken();
		String third = readTokens(callRefresh(second)).refreshToken();
		readTokens(callRefresh(third));

		// The very first token, three rotations ago.
		assertThat(callRefresh(original.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		assertThat(onlySession().getRevokedReason()).isEqualTo(AdminSessionService.REASON_TOKEN_REUSE);
	}

	@Test
	void rejectsARefreshTokenOnceItsSessionHasExpired() throws Exception {
		saveAdmin("rexpired@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("rexpired@tandf.example");

		expireSession(onlySession().getId());

		assertThat(callRefresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		// An expired session is not a theft signal, so it is left as it is rather than revoked.
		assertThat(onlySession().getRevokedAt()).isNull();
	}

	/**
	 * The token is only a lookup key, so anything that is not a stored fingerprint is simply unknown.
	 * There is no signature to forge and no audience to get wrong.
	 */
	@Test
	void rejectsARefreshTokenThatMatchesNoSession() throws Exception {
		saveAdmin("rnosession@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		loginSuccessfully("rnosession@tandf.example");

		assertThat(callRefresh("not-a-real-token").getResponse().getStatus()).isEqualTo(401);
		assertThat(callRefresh("aaa.bbb.ccc").getResponse().getStatus()).isEqualTo(401);
		assertThat(callRefresh(randomOpaqueLookingToken()).getResponse().getStatus()).isEqualTo(401);

		// None of that touched the live session.
		assertThat(onlySession().getRevokedAt()).isNull();
	}

	@Test
	void refusesToRefreshForAnAdminSuspendedAfterLogin() throws Exception {
		AdminUser admin = saveAdmin("suspendlater@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("suspendlater@tandf.example");

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
		AdminLoginResponse tokens = loginSuccessfully("deleted@tandf.example");

		this.adminUserRepository.deleteAll();

		assertThat(callRefresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void picksUpARoleChangeOnTheNextRefresh() throws Exception {
		AdminUser admin = saveAdmin("rolechange@tandf.example", AdminRole.PUBLISHER_ADMIN, AdminStatus.ACTIVE,
				"publisher-1", null);
		AdminLoginResponse original = loginSuccessfully("rolechange@tandf.example");
		assertThat(roleOf(original.accessToken())).isEqualTo("PUBLISHER_ADMIN");

		admin.setRole(AdminRole.SUPER_ADMIN);
		admin.setPublisherId(null);
		this.adminUserRepository.save(admin);

		TokenPair refreshed = readTokens(callRefresh(original.refreshToken()));

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
