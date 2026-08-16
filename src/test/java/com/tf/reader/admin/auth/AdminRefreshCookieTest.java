package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.dto.TokenPair;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;

import jakarta.servlet.http.Cookie;

/**
 * The refresh token as an {@code HttpOnly} cookie, which is what makes a console reload survive.
 *
 * <p>The behaviour that matters is the bodyless refresh: on a page load the console has no token in
 * JavaScript and cannot read the cookie to check for one, so it posts nothing at all and relies on the
 * browser to attach it.
 */
class AdminRefreshCookieTest extends AbstractAdminAuthIntegrationTest {

	private static final String COOKIE = "adminRefresh";

	@Test
	@DisplayName("login sets the refresh token as an HttpOnly cookie with the contract's attributes")
	void loginSetsTheCookie() throws Exception {
		saveAdmin("cookie.login@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = performLogin("cookie.login@tandf.example", PASSWORD);
		AdminLoginResponse login = readLogin(result);

		Cookie cookie = result.getResponse().getCookie(COOKIE);
		assertThat(cookie).isNotNull();
		assertThat(cookie.getValue()).isEqualTo(login.refreshToken());
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getPath()).isEqualTo("/api/admin/v1/auth");
		assertThat(cookie.getMaxAge()).isEqualTo(login.refreshExpiresIn());
		assertThat(setCookieHeader(result)).contains("SameSite=Strict");
	}

	/** The console's restore on page load: no body, no Content-Type, nothing but the cookie. */
	@Test
	@DisplayName("a bodyless refresh carrying only the cookie succeeds")
	void bodylessRefreshWithTheCookieSucceeds() throws Exception {
		saveAdmin("cookie.restore@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse login = loginSuccessfully("cookie.restore@tandf.example");

		MvcResult result = this.mockMvc
				.perform(post(REFRESH_PATH).cookie(new Cookie(COOKIE, login.refreshToken())).with(csrf()))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		TokenPair refreshed = readTokens(result);
		assertThat(refreshed.accessToken()).isNotBlank();
		assertThat(refreshed.refreshToken()).isNotBlank().isNotEqualTo(login.refreshToken());

		// The rotated token replaces the spent one, so the next reload has something live to send.
		assertThat(result.getResponse().getCookie(COOKIE)).isNotNull()
				.satisfies(cookie -> assertThat(cookie.getValue()).isEqualTo(refreshed.refreshToken()));
	}

	@Test
	@DisplayName("the cookie wins when a body is also present")
	void theCookieIsPreferredOverTheBody() throws Exception {
		saveAdmin("cookie.wins@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse first = loginSuccessfully("cookie.wins@tandf.example");
		AdminLoginResponse second = loginSuccessfully("cookie.wins@tandf.example");

		// Two live sessions. The cookie names one, the body the other, and only one may be spent.
		MvcResult result = this.mockMvc
				.perform(post(REFRESH_PATH).cookie(new Cookie(COOKIE, first.refreshToken()))
						.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
						.content("{\"refreshToken\": \"%s\"}".formatted(second.refreshToken())).with(csrf()))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);

		// The cookie's token is spent, so replaying it now fails.
		assertThat(callRefresh(first.refreshToken()).getResponse().getStatus()).isEqualTo(401);

		// The body's token was never touched, so it still works.
		assertThat(callRefresh(second.refreshToken()).getResponse().getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("logout accepts the cookie alone and clears it")
	void logoutClearsTheCookie() throws Exception {
		saveAdmin("cookie.logout@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse login = loginSuccessfully("cookie.logout@tandf.example");

		MvcResult result = this.mockMvc
				.perform(post(LOGOUT_PATH).cookie(new Cookie(COOKIE, login.refreshToken())).with(csrf()))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(204);

		Cookie cleared = result.getResponse().getCookie(COOKIE);
		assertThat(cleared).isNotNull();
		assertThat(cleared.getValue()).isEmpty();
		assertThat(cleared.getMaxAge()).isZero();

		// The session really is revoked, not just forgotten by the browser.
		assertThat(callRefresh(login.refreshToken()).getResponse().getStatus()).isEqualTo(401);
		assertThat(callMe(login.accessToken()).getResponse().getStatus()).isEqualTo(401);
	}

	@Test
	@DisplayName("a stale cookie is rejected like any other spent token")
	void aStaleCookieIsRejected() throws Exception {
		saveAdmin("cookie.stale@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse login = loginSuccessfully("cookie.stale@tandf.example");
		readTokens(callRefresh(login.refreshToken()));

		MvcResult result = this.mockMvc
				.perform(post(REFRESH_PATH).cookie(new Cookie(COOKIE, login.refreshToken())).with(csrf()))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
	}

	/**
	 * The cookie is the reason CSRF matters: a browser attaches it to a cross-site POST without being
	 * asked, so the two endpoints that read it have to demand a token as well.
	 */
	@Test
	@DisplayName("refresh and logout refuse a request with no CSRF token, even with a valid cookie")
	void theCookieEndpointsRequireACsrfToken() throws Exception {
		saveAdmin("cookie.csrf@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse login = loginSuccessfully("cookie.csrf@tandf.example");

		assertThat(this.mockMvc.perform(post(REFRESH_PATH).cookie(new Cookie(COOKIE, login.refreshToken())))
				.andReturn().getResponse().getStatus()).isEqualTo(403);

		assertThat(this.mockMvc.perform(post(LOGOUT_PATH).cookie(new Cookie(COOKIE, login.refreshToken())))
				.andReturn().getResponse().getStatus()).isEqualTo(403);

		// Refused, not spent: the honest holder can still use it.
		assertThat(callRefresh(login.refreshToken()).getResponse().getStatus()).isEqualTo(200);
	}

	/**
	 * Login is exempt on purpose. It carries no cookie authority, and requiring a token would mean
	 * fetching one before anybody could sign in.
	 */
	@Test
	@DisplayName("login needs no CSRF token and still issues one for the next call")
	void loginIsExemptAndSeedsTheToken() throws Exception {
		saveAdmin("cookie.exempt@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = performLogin("cookie.exempt@tandf.example", PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull()
				.satisfies(cookie -> assertThat(cookie.getValue()).isNotBlank());
	}

	/**
	 * The response carries more than one {@code Set-Cookie}, since the chain also issues the CSRF
	 * token, so pick the one under test rather than whichever came first.
	 */
	private static String setCookieHeader(MvcResult result) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(header -> header.startsWith(COOKIE + "="))
				.findFirst()
				.orElseThrow(() -> new AssertionError("No Set-Cookie for " + COOKIE));
	}

}
