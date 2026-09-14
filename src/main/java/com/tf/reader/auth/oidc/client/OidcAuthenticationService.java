package com.tf.reader.auth.oidc.client;

import com.tf.reader.auth.oidc.validation.OidcIdTokenValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.tf.reader.auth.dto.TokenResponse;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.service.ReaderSessionService;
import com.tf.reader.auth.service.ReaderSessionService.IssuedRefreshToken;
import com.tf.reader.auth.token.AuthorizationCodeStore;
import com.tf.reader.auth.token.IssuedToken;
import com.tf.reader.auth.token.TokenService;

/**
 * A whole individual sign-in, start to finish: the reader's own username and password go
 * straight to the provider, and a validated ID token comes back in the same request.
 *
 * <p>Knows nothing about HTTP - no servlet, no request, no response - which is what keeps the
 * whole flow unit-testable without a browser and without a provider. {@link OidcController} does
 * the HTTP and nothing else.
 *
 * <p><b>Where this converges with SAML.</b> {@link #startPasswordLogin} ends at
 * {@code TokenService.issue(TnfUser)}, the same call
 * {@link com.tf.reader.auth.saml.SamlAuthenticationService} ends at, producing the same HS256
 * token validated by the same decoder on every later request. Below that line the application
 * cannot tell the two protocols apart, and that is the design:
 *
 * <pre>
 * SAML assertion ─┐
 *                 ├─→ TnfUser → TokenService → application JWT → CurrentUser → AuthorizationService
 * OIDC ID token ──┘
 * </pre>
 *
 * <p><b>The provider's ID token never becomes the application's token.</b> It is consumed here,
 * at sign-in, and does not leave this class. Handing it to the client as a bearer credential
 * would make our API's authorization depend on somebody else's token lifetime, somebody else's
 * claim set and somebody else's idea of who an administrator is.
 *
 * <p><b>The reader's own tokens are not returned from this call either.</b> They are minted here,
 * exactly once, and stashed behind a one-time id in {@link AuthorizationCodeStore} - the same
 * single-use, short-lived store the SAML leg's deep-link callback uses - so a caller of this
 * method never receives a token pair it did not explicitly redeem a moment later at
 * {@code POST /api/v1/auth/oidc/token}.
 */
@Service
public class OidcAuthenticationService {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(OidcAuthenticationService.class);

	private final OidcTokenClient tokenClient;
	private final OidcIdTokenValidator idTokenValidator;
	private final OidcUserMapper userMapper;
	private final TokenService tokenService;
	private final ReaderSessionService readerSessions;
	private final AuthorizationCodeStore authorizationCodes;
	private final Clock clock;

	public OidcAuthenticationService(OidcTokenClient tokenClient,
			OidcIdTokenValidator idTokenValidator, OidcUserMapper userMapper,
			TokenService tokenService, ReaderSessionService readerSessions,
			AuthorizationCodeStore authorizationCodes, Clock clock) {
		this.tokenClient = tokenClient;
		this.idTokenValidator = idTokenValidator;
		this.userMapper = userMapper;
		this.tokenService = tokenService;
		this.readerSessions = readerSessions;
		this.authorizationCodes = authorizationCodes;
		this.clock = clock;
	}

	/**
	 * Authenticates a reader's own username and password against the provider, mints an
	 * application token pair, and stashes it behind a one-time id.
	 *
	 * <p>The order of the steps is the security property, so it is worth reading as a list:
	 *
	 * <ol>
	 * <li><b>credential exchange</b> - server to server, with our client secret, using the
	 * password grant</li>
	 * <li><b>ID token</b> - signature against the provider's JWKS, issuer, audience, expiry</li>
	 * <li><b>user</b> - our own store, by email; the first sign-in for an email provisions it</li>
	 * <li><b>token</b> - ours, minted last, and stashed behind a one-time id</li>
	 * </ol>
	 *
	 * <p>A caller whose credentials the provider rejects never reaches {@code tokenService.issue},
	 * so a failed sign-in cannot produce a token pair at any point.
	 *
	 * @throws ApiException 401 if the provider rejects the credentials or the ID token does not
	 *                      validate
	 */
	public OidcStartResponse startPasswordLogin(String username, String password) {
		// STEP 1 - the credentials become tokens, on a connection the reader's client never sees.
		OidcTokenResponse tokens = tokenClient.exchangePassword(username, password);

		// STEP 2 - and the ID token becomes claims we are willing to believe.
		Jwt idToken = idTokenValidator.validate(tokens.idToken());

		// STEP 3 - who that is, here.
		TnfUser user = userMapper.map(idToken);

		// STEP 4 - our tokens, from the mapped user and nothing else.
		IssuedToken accessToken = tokenService.issue(user);
		IssuedRefreshToken refreshToken = readerSessions.createSession(user);
		log.info("Application JWT issued for {} via OIDC, expires at {}",
				user.userId(), accessToken.expiresAt());

		long expiresIn = Duration.between(accessToken.issuedAt(), accessToken.expiresAt()).getSeconds();
		TokenResponse tokenResponse =
				new TokenResponse(accessToken.token(), refreshToken.value(), expiresIn);

		String oidcTxnId = authorizationCodes.issue(tokenResponse);
		Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);

		return new OidcStartResponse(oidcTxnId, now.plus(authorizationCodes.lifetime()), now);
	}
}
