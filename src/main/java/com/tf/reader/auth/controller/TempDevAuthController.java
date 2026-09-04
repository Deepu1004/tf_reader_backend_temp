package com.tf.reader.auth.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.dto.TokenResponse;
import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.service.ReaderSessionService;
import com.tf.reader.auth.token.IssuedToken;
import com.tf.reader.auth.token.TokenService;

/**
 * TEMPORARY, TEST-ONLY. Deepak added this 2026-08-29 to manually verify the SAML integration
 * without a browser for every check. Delete this whole file — and its two matching permitAll /
 * authenticated entries in {@code UserSecurityConfig} if any were added for it — once that
 * verification work is done. Nothing here belongs in a real auth surface:
 *
 * <ul>
 *   <li>{@code /dev-token} mints a token for an arbitrary user with no authentication at all.</li>
 *   <li>{@code /dev-session-tokens} mints a brand new session (and therefore a new refresh token)
 *       for whoever is already authenticated, purely so a manual tester can see both halves of a
 *       token pair at once instead of only the access token an Alert or a log line shows.</li>
 * </ul>
 *
 * <p><b>{@code tnf.dev-auth.enabled} must be set explicitly, the same way {@code mock-oidc.enabled}
 * and {@code saml-mock.enabled} are.</b> {@code /dev-token} mints a token for anyone with no
 * check at all, so leaving this on by default anywhere but a developer's own machine is a full
 * auth bypass. {@code SecurityArchitectureTest} asserts {@code application.yml} ships it disabled.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "tnf.dev-auth", name = "enabled", havingValue = "true")
public class TempDevAuthController {

	private final TokenService tokenService;
	private final ReaderSessionService readerSessions;

	public TempDevAuthController(TokenService tokenService, ReaderSessionService readerSessions) {
		this.tokenService = tokenService;
		this.readerSessions = readerSessions;
	}

	/** Moved out of {@code AuthController} unchanged — same endpoint, same behaviour. */
	@PostMapping("/dev-token")
	public IssuedToken generateDevToken(
			@RequestParam(defaultValue = "usr_dev123") String userId,
			@RequestParam(defaultValue = "inst_7f3") String institutionId) {
		TnfUser user = new TnfUser(userId, UserType.INSTITUTION, institutionId, List.of("MEMBER"), List.of("col_law2024"));
		return tokenService.issue(user);
	}

	/**
	 * Both tokens for "the current session" — i.e. whichever identity the caller's own bearer
	 * token already proves. Not registered as {@code permitAll} anywhere, so
	 * {@code UserSecurityConfig}'s existing {@code anyRequest().authenticated()} default already
	 * does the "no session -> error" part: no bearer token, or an invalid one, never reaches this
	 * method at all and gets the usual 401 instead.
	 *
	 * <p>A fresh session (and refresh token) is minted on every call rather than reading one back
	 * out of storage - the refresh token is only ever a SHA-256 fingerprint at rest
	 * ({@code ReaderSessionService}'s own doc), so there is nothing to read back out.
	 */
	@PostMapping("/dev-session-tokens")
	public TokenResponse currentSessionTokens(@AuthenticationPrincipal CurrentUser currentUser) {
		TnfUser user = new TnfUser(currentUser.userId(), currentUser.type(), currentUser.institutionId(),
				currentUser.roles(), currentUser.collections());

		IssuedToken accessToken = tokenService.issue(user);
		ReaderSessionService.IssuedRefreshToken refreshToken = readerSessions.createSession(user);

		return new TokenResponse(accessToken.token(), refreshToken.value(),
				Duration.between(accessToken.issuedAt(), accessToken.expiresAt()).getSeconds());
	}
}
