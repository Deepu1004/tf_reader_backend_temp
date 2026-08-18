package com.tf.reader.admin.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.dto.AdminProfileResponse;
import com.tf.reader.admin.dto.LoginRequest;
import com.tf.reader.admin.dto.RefreshRequest;
import com.tf.reader.admin.dto.TokenPair;
import com.tf.reader.admin.service.AdminAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * The refresh token travels as an {@code HttpOnly} cookie, which is the only browser store an injected
 * script cannot read and the only one that survives a page load. That is what lets a console reload
 * keep the operator signed in. The access token stays in memory and stays a bearer header.
 *
 * <p>The cookie is written and read here rather than in the service, because it is a transport
 * detail: {@link AdminAuthService} still takes and returns a plain token string, and a non-browser
 * caller can still use the body.
 */
@RestController
@RequestMapping(path = "/api/admin/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin authentication", description = "Login, token refresh, logout and current-admin lookup.")
public class AdminAuthController {

	static final String REFRESH_COOKIE = "adminRefresh";

	/**
	 * Narrow on purpose. Only refresh and logout ever need the cookie, so no other admin request
	 * carries it and no other endpoint can leak it.
	 */
	static final String REFRESH_COOKIE_PATH = "/api/admin/v1/auth";

	private final AdminAuthService adminAuthService;

	public AdminAuthController(AdminAuthService adminAuthService) {
		this.adminAuthService = adminAuthService;
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Console sign in",
			description = "Verifies the password and opens a session. Invalid credentials and non-active "
					+ "accounts return the same 401, so responses cannot be used to discover which "
					+ "email addresses exist. Sets the refresh token as an HttpOnly adminRefresh cookie; "
					+ "the body still carries it for a caller that has no cookie jar.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "An access token and a refresh token."),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()),
			@ApiResponse(responseCode = "401", description = "Authentication failed.",
					content = @Content()) })
	public ResponseEntity<AdminLoginResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletResponse httpResponse) {

		AdminLoginResponse response = this.adminAuthService.login(request.email(), request.password());
		addRefreshCookie(httpResponse, response.refreshToken(), response.refreshExpiresIn());

		return ResponseEntity.ok(response);
	}

	/**
	 * No {@code consumes}: the console sends no body at all on a page load, and declaring a JSON
	 * content type would answer that with a 415 before the cookie was ever read.
	 */
	@PostMapping(path = "/refresh")
	@Operation(summary = "Trade a refresh token for a new access token",
			description = "Issues a new access token and rotates the refresh token. The presented refresh "
					+ "token stops working immediately, so a stolen one is usable at most once. Replaying "
					+ "an already-rotated token is rejected, and nothing else is revoked: the replacement "
					+ "session belonging to the honest holder keeps working. Takes the token from the "
					+ "adminRefresh cookie, or from the body when there is no cookie. Presenting neither "
					+ "is a 401, not a 400. Access tokens are never accepted here. Returns no user: "
					+ "refreshing proves nothing new about who the caller is.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "A new pair. The one you sent is now dead."),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()),
			@ApiResponse(responseCode = "401",
					description = "Unknown, expired, already used or revoked. All four are the same answer, "
							+ "and the only cure is signing in again.",
					content = @Content()) })
	public ResponseEntity<TokenPair> refresh(
			@Parameter(description = "The refresh token, set by a previous login or refresh. Preferred "
					+ "over the body.")
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
			@Valid @RequestBody(required = false) RefreshRequest request,
			HttpServletResponse httpResponse) {

		TokenPair tokens = this.adminAuthService.refresh(presentedToken(refreshCookie, request));
		addRefreshCookie(httpResponse, tokens.refreshToken(), tokens.refreshExpiresIn());

		return ResponseEntity.ok(tokens);
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "adminToken")
	@Operation(summary = "Who am I, and what may I do",
			description = "Returns the authenticated admin's profile. Never includes the password hash.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The signed in operator."),
			@ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked access token.",
					content = @Content()),
			@ApiResponse(responseCode = "403", description = "Authenticated but not permitted.",
					content = @Content()) })
	public AdminProfileResponse me(@AuthenticationPrincipal Jwt accessToken) {
		return AdminProfileResponse.from(this.adminAuthService.requireAdmin(accessToken.getSubject()));
	}

	/** Same reasoning as refresh for the missing {@code consumes}. */
	@PostMapping(path = "/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke a refresh token",
			description = "Marks the session revoked so the refresh token can never be exchanged again. "
					+ "Always 204, even for a token that never existed or was never sent, so nobody can "
					+ "probe which ones are live. Clears the adminRefresh cookie, so a reload after "
					+ "signing out lands on the login page. The console must drop its access token on its "
					+ "own side too.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Revoked, or was never valid. Same answer either way.",
					content = @Content()),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()) })
	public ResponseEntity<Void> logout(
			@Parameter(description = "The refresh token to revoke. Preferred over the body.")
			@CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
			@Valid @RequestBody(required = false) RefreshRequest request,
			HttpServletResponse httpResponse) {

		this.adminAuthService.logout(presentedToken(refreshCookie, request));
		addRefreshCookie(httpResponse, "", 0);

		return ResponseEntity.noContent().build();
	}

	/** Cookie first. The body is the fallback for a caller that has no cookie jar. */
	private static String presentedToken(String refreshCookie, RefreshRequest request) {
		if (refreshCookie != null && !refreshCookie.isBlank()) {
			return refreshCookie;
		}
		return (request != null) ? request.refreshToken() : null;
	}

	/**
	 * Added to the servlet response rather than to the {@link ResponseEntity}, because entity headers
	 * replace same-named headers already on the response and would drop the {@code XSRF-TOKEN} cookie
	 * the CSRF filter had just written. An empty value with {@code Max-Age=0} clears it, which is what
	 * logout sends.
	 *
	 * @param maxAgeSeconds the session's remaining life, so the cookie dies with the session rather
	 *                      than outliving it. On a refresh this is what is left of the original twelve
	 *                      hours, because rotation never extends a session.
	 */
	private static void addRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
				.httpOnly(true)
				.secure(true)
				.sameSite("Strict")
				.path(REFRESH_COOKIE_PATH)
				.maxAge(maxAgeSeconds)
				.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

}
