package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.service.AdminSessionService;

/**
 * Logout takes the refresh token in the body, needs no access token, and always answers 204.
 *
 * <p>It must still have a real server-side effect: the session is revoked, which kills both the
 * refresh token and, on the next request, the access token.
 */
class AdminLogoutTest extends AbstractAdminAuthIntegrationTest {

	@Test
	void revokesTheSessionAndReturnsNoContent() throws Exception {
		saveAdmin("logout@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logout@tandf.example");

		MvcResult result = callLogout(tokens.refreshToken());

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString()).isEmpty();

		AdminSession session = onlySession();
		assertThat(session.getRevokedAt()).isNotNull();
		assertThat(session.getRevokedReason()).isEqualTo(AdminSessionService.REASON_LOGOUT);
	}

	@Test
	void makesTheRefreshTokenUnusableAfterLogout() throws Exception {
		saveAdmin("logoutrefresh@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logoutrefresh@tandf.example");

		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(204);

		assertThat(callRefresh(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * The point of validating the session on every request: the access token is still unexpired and
	 * correctly signed, yet it stops working the moment the session is revoked.
	 */
	@Test
	void makesTheAccessTokenUnusableImmediatelyAfterLogout() throws Exception {
		saveAdmin("logoutaccess@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logoutaccess@tandf.example");

		assertThat(callMe(tokens.accessToken()).getResponse().getStatus()).isEqualTo(200);
		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(204);

		assertThat(callMe(tokens.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * Repeating the call is safe and reports nothing different, while the original revocation reason
	 * and timestamp survive untouched.
	 */
	@Test
	void isIdempotent() throws Exception {
		saveAdmin("logouttwice@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logouttwice@tandf.example");

		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(204);
		AdminSession afterFirst = onlySession();

		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(204);

		AdminSession afterSecond = onlySession();
		assertThat(afterSecond.getRevokedAt()).isEqualTo(afterFirst.getRevokedAt());
		assertThat(afterSecond.getRevokedReason()).isEqualTo(AdminSessionService.REASON_LOGOUT);
	}

	@Test
	void leavesOtherSessionsOfTheSameAdminAlone() throws Exception {
		saveAdmin("twosessions@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse first = loginSuccessfully("twosessions@tandf.example");
		AdminLoginResponse second = loginSuccessfully("twosessions@tandf.example");

		assertThat(this.adminSessionRepository.count()).isEqualTo(2);
		assertThat(callLogout(first.refreshToken()).getResponse().getStatus()).isEqualTo(204);

		// Logging out of one device must not sign the admin out everywhere.
		assertThat(callMe(first.accessToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(second.accessToken()).getResponse().getStatus()).isEqualTo(200);
		assertThat(callRefresh(second.refreshToken()).getResponse().getStatus()).isEqualTo(200);
	}

	/**
	 * A token that never existed gets the same 204 as a real one, so the endpoint cannot be used to
	 * discover which refresh tokens are live.
	 */
	@Test
	void answersNoContentForATokenThatNeverExisted() throws Exception {
		assertThat(callLogout("not-a-jwt").getResponse().getStatus()).isEqualTo(204);

		saveAdmin("logoutprobe@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logoutprobe@tandf.example");

		MvcResult unknown = callLogout("aaa.bbb.ccc");
		MvcResult real = callLogout(tokens.refreshToken());

		assertThat(unknown.getResponse().getStatus()).isEqualTo(real.getResponse().getStatus());
		assertThat(unknown.getResponse().getContentAsString())
				.isEqualTo(real.getResponse().getContentAsString());
	}

	/**
	 * An access token is not a refresh token here either. It is accepted with the same 204 as any
	 * other unusable value, but it revokes nothing.
	 */
	@Test
	void doesNotAcceptAnAccessTokenInPlaceOfTheRefreshToken() throws Exception {
		saveAdmin("logoutwrongtoken@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logoutwrongtoken@tandf.example");

		assertThat(callLogout(tokens.accessToken()).getResponse().getStatus()).isEqualTo(204);

		assertThat(onlySession().getRevokedAt()).isNull();
		assertThat(callMe(tokens.accessToken()).getResponse().getStatus()).isEqualTo(200);
	}

	/** An unissued token that looks exactly like a real one still gets the uniform 204. */
	@Test
	void answersNoContentForAnUnissuedOpaqueToken() throws Exception {
		saveAdmin("logoutopaque@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		loginSuccessfully("logoutopaque@tandf.example");

		assertThat(callLogout(randomOpaqueLookingToken()).getResponse().getStatus()).isEqualTo(204);

		assertThat(onlySession().getRevokedAt()).isNull();
	}

	/** A token the session has already rotated away from still identifies it, and revoking is safe. */
	@Test
	void revokesTheSessionForASupersededRefreshToken() throws Exception {
		saveAdmin("logoutsuperseded@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("logoutsuperseded@tandf.example");
		callRefresh(tokens.refreshToken());

		assertThat(callLogout(tokens.refreshToken()).getResponse().getStatus()).isEqualTo(204);

		assertThat(onlySession().getRevokedAt()).isNotNull();
	}

	@Test
	void rejectsAnEmptyOrMissingRefreshTokenField() throws Exception {
		assertThat(callLogoutWithRawBody("{}").getResponse().getStatus()).isEqualTo(400);
		assertThat(callLogout("").getResponse().getStatus()).isEqualTo(400);
	}

}
