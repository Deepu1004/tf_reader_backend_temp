package com.tf.reader.auth.b2c;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.b2c.B2cAuthenticationService.B2cLoginResult;
import com.tf.reader.auth.dto.TokenResponse;
import com.tf.reader.auth.saml.SamlAuthenticationSuccessHandler;
import com.tf.reader.auth.service.ReaderSessionService;
import com.tf.reader.auth.service.ReaderSessionService.IssuedRefreshToken;
import com.tf.reader.auth.token.AuthorizationCodeStore;
import com.tf.reader.common.error.ApiException;

/**
 * The two B2C endpoints: starting an individual, email-and-password sign-in, and receiving the
 * provider's callback.
 *
 * <p><b>Unlike {@link com.tf.reader.auth.oidc.client.OidcController}'s callback, this one
 * finishes a sign-in the way {@link SamlAuthenticationSuccessHandler} does</b>: a refresh token
 * is minted, both tokens are stashed behind a one-time code, and the browser is redirected to the
 * app's deep link carrying it. That is what a mobile client can actually receive - this callback,
 * like the SAML ACS, is reached by a browser redirect from the provider, not a fetch call the app
 * can read a JSON body from.
 *
 * <p><b>Both routes are public, and both have to be.</b> {@code /start} is how a caller obtains a
 * credential, so it cannot require one; {@code /callback} is entered by a browser redirect from
 * the provider, which carries no bearer token and never will.
 */
@RestController
@RequestMapping("/api/v1/auth/b2c")
public class B2cController {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(B2cController.class);

	private final B2cAuthenticationService authentication;
	private final ReaderSessionService readerSessions;
	private final AuthorizationCodeStore authorizationCodes;

	public B2cController(B2cAuthenticationService authentication,
			ReaderSessionService readerSessions, AuthorizationCodeStore authorizationCodes) {
		this.authentication = authentication;
		this.readerSessions = readerSessions;
		this.authorizationCodes = authorizationCodes;
	}

	/**
	 * Begins an individual sign-in over B2C.
	 *
	 * <p>No institution to name and no request body: unlike SAML and institutional OIDC, this flow
	 * has nothing for a caller to say up front.
	 */
	@PostMapping("/start")
	public B2cStartResponse start() {
		return authentication.start();
	}

	/**
	 * Where the identity provider sends the browser back.
	 *
	 * <p>On success, mints a refresh token and a one-time code exactly as
	 * {@link SamlAuthenticationSuccessHandler} does, then redirects to the same deep link that
	 * SAML and institutional OIDC use - one callback intake for the whole app, whichever protocol
	 * somebody signed in through. On failure, redirects to the same deep link carrying
	 * {@code ?error=} rather than throwing: the browser is mid-redirect either way, and a thrown
	 * exception here would answer a browser navigation with a JSON body it cannot do anything
	 * with.
	 */
	@GetMapping("/callback")
	public void callback(
			@RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "state") String state,
			@RequestParam(name = "error", required = false) String error,
			HttpServletResponse response) throws IOException {

		log.info("B2C callback received{}", (error != null) ? " carrying provider error: " + error : "");

		try {
			B2cLoginResult result = authentication.complete(code, state);

			IssuedRefreshToken refreshToken = readerSessions.createSession(result.user());
			long expiresIn = Duration.between(result.serverTime(), result.expiresAt()).getSeconds();
			String oneTimeCode = authorizationCodes.issue(
					new TokenResponse(result.token(), refreshToken.value(), expiresIn));

			response.sendRedirect(
					SamlAuthenticationSuccessHandler.DEEP_LINK_CALLBACK + "?code=" + oneTimeCode);
		}
		catch (ApiException failure) {
			response.sendRedirect(SamlAuthenticationSuccessHandler.DEEP_LINK_CALLBACK
					+ "?error=" + failure.getCode().name());
		}
	}
}
