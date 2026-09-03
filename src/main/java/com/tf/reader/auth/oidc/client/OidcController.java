package com.tf.reader.auth.oidc.client;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.dto.TokenResponse;
import com.tf.reader.auth.oidc.client.OidcAuthenticationService.OidcLoginResult;
import com.tf.reader.auth.service.ReaderSessionService;
import com.tf.reader.auth.service.ReaderSessionService.IssuedRefreshToken;
import com.tf.reader.auth.token.AuthorizationCodeStore;
import com.tf.reader.common.error.ApiException;

/**
 * The two OIDC endpoints: starting the individual, no-institution sign-in, and receiving the
 * provider's callback.
 *
 * <p>HTTP only. Every decision lives in {@link OidcAuthenticationService}, which is why that
 * class can be tested without a servlet and this one has almost nothing in it. The same split
 * the SAML leg uses, where the handlers do HTTP and the service does the deciding.
 *
 * <p><b>Both routes are public, and both have to be.</b> {@code /start} is how a caller obtains
 * a credential, so it cannot require one; {@code /callback} is entered by a browser redirect
 * from the provider, which carries no bearer token and never will. {@code /callback} takes a
 * code and a state, both of which are meaningless unless they match a sign-in this backend
 * started.
 *
 * <p><b>The callback finishes a sign-in the same way the SAML ACS does</b>: a refresh token is
 * minted, both tokens are stashed behind a one-time code
 * ({@link AuthorizationCodeStore#DEEP_LINK_CALLBACK}), and the browser is redirected to the app's
 * deep link carrying it. That is what a mobile client can actually receive - this callback, like
 * the SAML ACS, is reached by a browser redirect from the provider, not a fetch call the app can
 * read a JSON body from. The two legs share this constant rather than one importing the other's
 * handler, which is what {@code SecurityArchitectureTest} requires of them.
 *
 * <p><b>No session is created by either.</b> State and nonce live in
 * {@link OidcTransactionStore}, server-side, so the whole OIDC flow runs on the stateless API
 * chain - there is no JSESSIONID for anybody to reuse as a second credential, which is the bug
 * {@code StatelessApiTest} exists to keep fixed on the SAML side.
 */
@RestController
@RequestMapping("/api/v1/auth/oidc")
public class OidcController {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(OidcController.class);

	private final OidcAuthenticationService authentication;
	private final ReaderSessionService readerSessions;
	private final AuthorizationCodeStore authorizationCodes;

	public OidcController(OidcAuthenticationService authentication,
			ReaderSessionService readerSessions, AuthorizationCodeStore authorizationCodes) {
		this.authentication = authentication;
		this.readerSessions = readerSessions;
		this.authorizationCodes = authorizationCodes;
	}

	/**
	 * Begins an individual sign-in over OIDC.
	 *
	 * <p>No institution to name and no request body: unlike SAML, this flow has nothing for a
	 * caller to say up front.
	 */
	@PostMapping("/start")
	public OidcStartResponse start() {
		return authentication.start();
	}

	/**
	 * Where the identity provider sends the browser back.
	 *
	 * <p>On success, mints a refresh token and a one-time code exactly as
	 * {@link SamlAuthenticationSuccessHandler} does, then redirects to the same deep link that
	 * SAML uses - one callback intake for the whole app, whichever protocol somebody signed in
	 * through. On failure, redirects to the same deep link carrying {@code ?error=} rather than
	 * throwing: the browser is mid-redirect either way, and a thrown exception here would answer
	 * a browser navigation with a JSON body it cannot do anything with.
	 *
	 * <p>{@code state} is required rather than optional: a callback without one cannot be matched
	 * to a sign-in, and Spring rejecting it as a missing parameter says exactly that. {@code code}
	 * is optional at this level so that a provider-side failure - the user cancelling, say, which
	 * arrives as {@code ?error=access_denied} with no code - is refused by our own service with
	 * our own error body, rather than by the framework with its own.
	 */
	@GetMapping("/callback")
	public void callback(
			@RequestParam(name = "code", required = false) String code,
			@RequestParam(name = "state") String state,
			@RequestParam(name = "error", required = false) String error,
			HttpServletResponse response) throws IOException {

		// The provider's own error code is short and fixed by the specification, so it is safe to
		// log; its error_description is not read at all, because that is where correlation ids and
		// configuration detail live. Neither is ever returned to the client.
		log.info("OIDC callback received{}", (error != null) ? " carrying provider error: " + error : "");

		try {
			OidcLoginResult result = authentication.complete(code, state);

			IssuedRefreshToken refreshToken = readerSessions.createSession(result.user());
			long expiresIn = Duration.between(result.serverTime(), result.expiresAt()).getSeconds();
			String oneTimeCode = authorizationCodes.issue(
					new TokenResponse(result.token(), refreshToken.value(), expiresIn));

			response.sendRedirect(
					AuthorizationCodeStore.DEEP_LINK_CALLBACK + "?code=" + oneTimeCode);
		}
		catch (ApiException failure) {
			response.sendRedirect(AuthorizationCodeStore.DEEP_LINK_CALLBACK
					+ "?error=" + failure.getCode().name());
		}
	}
}
