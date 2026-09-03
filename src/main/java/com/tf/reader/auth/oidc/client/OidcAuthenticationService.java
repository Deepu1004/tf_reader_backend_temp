package com.tf.reader.auth.oidc.client;

import com.tf.reader.auth.oidc.validation.OidcIdTokenValidator;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.token.IssuedToken;
import com.tf.reader.auth.token.TokenService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * The two halves of an OIDC sign-in: starting one, and completing the one that comes back.
 *
 * <p>Knows nothing about HTTP - no servlet, no request, no response - which is what keeps the
 * whole flow unit-testable without a browser and without a provider. {@link OidcController} does
 * the HTTP and nothing else.
 *
 * <p><b>Where this converges with SAML.</b> {@link #complete} ends at
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
 */
@Service
public class OidcAuthenticationService {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(OidcAuthenticationService.class);

	private final OidcTransactionStore transactions;
	private final OidcTokenClient tokenClient;
	private final OidcIdTokenValidator idTokenValidator;
	private final OidcUserMapper userMapper;
	private final TokenService tokenService;
	private final OidcProperties properties;
	private final Clock clock;

	public OidcAuthenticationService(OidcTransactionStore transactions,
			OidcTokenClient tokenClient, OidcIdTokenValidator idTokenValidator,
			OidcUserMapper userMapper, TokenService tokenService, OidcProperties properties,
			Clock clock) {
		this.transactions = transactions;
		this.tokenClient = tokenClient;
		this.idTokenValidator = idTokenValidator;
		this.userMapper = userMapper;
		this.tokenService = tokenService;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Opens a sign-in and returns the url the browser must be sent to.
	 *
	 * <p>Authenticates nobody and mints no token. No institution to resolve and none to refuse a
	 * caller over - this is the whole point of the flow.
	 */
	public OidcStartResponse start() {
		OidcTransaction transaction = transactions.open();
		log.info("OIDC transaction created: {}", transaction.id());

		return new OidcStartResponse(
				transaction.id(),
				authorizationUrl(transaction),
				transaction.expiresAt().truncatedTo(ChronoUnit.SECONDS),
				clock.instant().truncatedTo(ChronoUnit.SECONDS));
	}

	/**
	 * Completes the sign-in a callback refers to.
	 *
	 * <p>The order of the steps is the security property, so it is worth reading as a list:
	 *
	 * <ol>
	 * <li><b>state</b> - the transaction is consumed by it. Unknown, expired or already-used
	 * state finds nothing and the flow stops here, before any network call is made</li>
	 * <li><b>code exchange</b> - server to server, with our client secret</li>
	 * <li><b>ID token</b> - signature against the provider's JWKS, issuer, audience, expiry,
	 * then the nonce against this transaction</li>
	 * <li><b>user</b> - our own store, by email; the first sign-in for an email provisions it</li>
	 * <li><b>token</b> - ours, minted last</li>
	 * </ol>
	 *
	 * <p>Note what that ordering buys: a caller who did not start a sign-in never reaches the
	 * token endpoint, and a user we could not map never reaches {@code tokenService.issue}, so a
	 * failed sign-in cannot produce a token at any point.
	 *
	 * @param code  the authorization code from the callback
	 * @param state the state from the callback - the ONLY thing that ties this callback to a
	 *              sign-in this backend started
	 * @throws ApiException 401 if the state is unknown/expired/used, the exchange fails, or the
	 *                      ID token does not validate
	 */
	public OidcLoginResult complete(String code, String state) {
		if (code == null || code.isBlank()) {
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"This callback carried no authorization code.");
		}

		// STEP 1 - state. Consuming is the check: see OidcTransactionStore.consume.
		OidcTransaction transaction = transactions.consume(state)
				.orElseThrow(() -> {
					log.warn("OIDC state validation FAILED - no in-flight sign-in matches this callback");
					return new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
							"This sign-in could not be matched to a transaction we started.");
				});
		log.info("OIDC state validation succeeded for transaction {}", transaction.id());

		// STEP 2 - the code becomes tokens, on a connection the browser never sees.
		OidcTokenResponse tokens = tokenClient.exchangeAuthorizationCode(code);

		// STEP 3 - and the ID token becomes claims we are willing to believe.
		Jwt idToken = idTokenValidator.validate(tokens.idToken(), transaction);

		// STEP 4 - who that is, here.
		TnfUser user = userMapper.map(idToken);

		// STEP 5 - our token, from the mapped user and nothing else.
		IssuedToken token = tokenService.issue(user);
		log.info("Application JWT issued for {} via OIDC, expires at {}",
				user.userId(), token.expiresAt());

		return new OidcLoginResult(token.token(), token.expiresAt(),
				clock.instant().truncatedTo(ChronoUnit.SECONDS),
				userMapper.resolveSubject(idToken), user);
	}

	/**
	 * The provider's authorization endpoint, with our state and nonce.
	 *
	 * <p>Every part of this url is configuration. Nothing in it comes from the request - in
	 * particular {@code redirect_uri}, which is the one parameter an attacker would most like to
	 * influence, and which is therefore read from {@link OidcProperties} and never from a caller.
	 */
	private String authorizationUrl(OidcTransaction transaction) {
		return UriComponentsBuilder.fromUriString(properties.authorizationUri())
				.queryParam("response_type", "code")
				.queryParam("client_id", properties.clientId())
				.queryParam("redirect_uri", properties.redirectUri())
				.queryParam("scope", properties.scopeParameter())
				.queryParam("state", transaction.state())
				.queryParam("nonce", transaction.nonce())
				.build()
				.encode()
				.toUriString();
	}

	/**
	 * What a completed sign-in produced.
	 *
	 * <p>{@code oidcSubject} is the provider's stable identifier for this identity - evidence that
	 * the right transaction and the right identity met, not a credential in itself.
	 *
	 * <p><b>The provider's ID token is deliberately not a component of this record</b>, so there
	 * is no path by which it could be serialised to a client or written to a log.
	 */
	public record OidcLoginResult(String token, Instant expiresAt, Instant serverTime,
			String oidcSubject, TnfUser user) {
	}
}
