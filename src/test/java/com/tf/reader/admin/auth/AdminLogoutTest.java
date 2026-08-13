package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.TokenResponse;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.service.AdminSessionService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Logout must have a real server-side effect: the session is revoked, which kills both the refresh
 * token and, on the next request, the access token.
 */
class AdminLogoutTest extends AbstractAdminAuthIntegrationTest {

	@Test
	void revokesTheSessionForAnAuthenticatedAdmin() throws Exception {
		saveAdmin("logout@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("logout@tandf.example");

		MvcResult result = callLogout(tokens.accessToken());

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentAsString()).contains("\"sessionRevoked\":true");

		AdminSession session = onlySession();
		assertThat(session.getRevokedAt()).isNotNull();
		assertThat(session.getRevokedReason()).isEqualTo(AdminSessionService.REASON_LOGOUT);
	}

	@Test
	void makesTheRefreshTokenUnusableAfterLogout() throws Exception {
		saveAdmin("logoutrefresh@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("logoutrefresh@tandf.example");

		assertThat(callLogout(tokens.accessToken()).getResponse().getStatus()).isEqualTo(200);

		assertThat(callRefresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * The point of validating the session on every request: the access token is still unexpired and
	 * correctly signed, yet it stops working the moment the session is revoked.
	 */
	@Test
	void makesTheAccessTokenUnusableImmediatelyAfterLogout() throws Exception {
		saveAdmin("logoutaccess@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("logoutaccess@tandf.example");

		assertThat(callMe(tokens.accessToken()).getResponse().getStatus()).isEqualTo(200);
		assertThat(callLogout(tokens.accessToken()).getResponse().getStatus()).isEqualTo(200);

		assertThat(callMe(tokens.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * A second logout reports that it changed nothing, but the session stays revoked and the original
	 * revocation reason and timestamp are preserved.
	 */
	@Test
	void isIdempotent() throws Exception {
		saveAdmin("logouttwice@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("logouttwice@tandf.example");

		assertThat(callLogout(tokens.accessToken()).getResponse().getContentAsString())
				.contains("\"sessionRevoked\":true");
		AdminSession afterFirst = onlySession();

		// The second call is rejected because the token's session is gone, which is itself the
		// correct outcome: the caller is already logged out.
		assertThat(callLogout(tokens.accessToken()).getResponse().getStatus()).isEqualTo(401);

		AdminSession afterSecond = onlySession();
		assertThat(afterSecond.getRevokedAt()).isEqualTo(afterFirst.getRevokedAt());
		assertThat(afterSecond.getRevokedReason()).isEqualTo(AdminSessionService.REASON_LOGOUT);
	}

	@Test
	void leavesOtherSessionsOfTheSameAdminAlone() throws Exception {
		saveAdmin("twosessions@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse first = loginSuccessfully("twosessions@tandf.example");
		TokenResponse second = loginSuccessfully("twosessions@tandf.example");

		assertThat(this.adminSessionRepository.count()).isEqualTo(2);
		assertThat(callLogout(first.accessToken()).getResponse().getStatus()).isEqualTo(200);

		// Logging out of one device must not sign the admin out everywhere.
		assertThat(callMe(first.accessToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(second.accessToken()).getResponse().getStatus()).isEqualTo(200);
		assertThat(callRefresh(second.refreshToken()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	void rejectsLogoutWithoutAuthentication() throws Exception {
		assertThat(this.mockMvc.perform(post(LOGOUT_PATH)).andReturn().getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsLogoutWithAnInvalidToken() throws Exception {
		assertThat(callLogout("not-a-jwt").getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	void rejectsLogoutWithARefreshToken() throws Exception {
		saveAdmin("logoutwrongtoken@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		TokenResponse tokens = loginSuccessfully("logoutwrongtoken@tandf.example");

		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		// The session survives, because the attempt never authenticated in the first place.
		assertThat(onlySession().getRevokedAt()).isNull();
	}

}
