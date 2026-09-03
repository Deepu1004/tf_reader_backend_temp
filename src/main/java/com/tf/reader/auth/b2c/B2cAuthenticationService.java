package com.tf.reader.auth.b2c;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.oidc.client.OidcTokenResponse;
import com.tf.reader.auth.token.IssuedToken;
import com.tf.reader.auth.token.TokenService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * The two halves of a B2C sign-in: starting one, and completing the one that comes back.
 *
 * <p>The individual-flow counterpart of
 * {@link com.tf.reader.auth.oidc.client.OidcAuthenticationService} - the same six steps, minus
 * the institution-recovery step neither the transaction nor the mapper have here, plus one this
 * flow adds that the other never needed: an identity that authenticates but has never been seen
 * before is provisioned, not refused.
 *
 * <p>Knows nothing about HTTP, for the same reason its OIDC counterpart doesn't -
 * {@link B2cController} does the HTTP and nothing else.
 *
 * <p><b>Where this converges with SAML and institutional OIDC.</b> {@link #complete} ends at the
 * same {@code TokenService.issue(TnfUser)} either of them do, producing the same kind of
 * application JWT. Below that line the application cannot tell any of the three protocols apart.
 */
@Service
public class B2cAuthenticationService {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(B2cAuthenticationService.class);

	private final B2cTransactionStore transactions;
	private final B2cTokenClient tokenClient;
	private final B2cIdTokenValidator idTokenValidator;
	private final B2cUserMapper userMapper;
	private final TokenService tokenService;
	private final B2cProperties properties;
	private final Clock clock;

	public B2cAuthenticationService(B2cTransactionStore transactions, B2cTokenClient tokenClient,
			B2cIdTokenValidator idTokenValidator, B2cUserMapper userMapper,
			TokenService tokenService, B2cProperties properties, Clock clock) {
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
	 * <p>No institution to resolve, and none to refuse a caller over - this is the whole point of
	 * the flow.
	 */
	public B2cStartResponse start() {
		B2cTransaction transaction = transactions.open();
		log.info("B2C transaction created: {}", transaction.id());

		return new B2cStartResponse(
				transaction.id(),
				authorizationUrl(transaction),
				transaction.expiresAt().truncatedTo(ChronoUnit.SECONDS),
				clock.instant().truncatedTo(ChronoUnit.SECONDS));
	}

	/**
	 * Completes the sign-in a callback refers to.
	 *
	 * @param code  the authorization code from the callback
	 * @param state the state from the callback - the ONLY thing that ties this callback to a
	 *              sign-in this backend started
	 * @throws ApiException 401 if the state is unknown/expired/used, the exchange fails, or the ID
	 *                      token does not validate
	 */
	public B2cLoginResult complete(String code, String state) {
		if (code == null || code.isBlank()) {
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"This callback carried no authorization code.");
		}

		B2cTransaction transaction = transactions.consume(state)
				.orElseThrow(() -> {
					log.warn("B2C state validation FAILED - no in-flight sign-in matches this callback");
					return new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
							"This sign-in could not be matched to a transaction we started.");
				});
		log.info("B2C state validation succeeded for transaction {}", transaction.id());

		OidcTokenResponse tokens = tokenClient.exchangeAuthorizationCode(code);
		Jwt idToken = idTokenValidator.validate(tokens.idToken(), transaction);

		TnfUser user = userMapper.map(idToken);

		IssuedToken token = tokenService.issue(user);
		log.info("Application JWT issued for {} via B2C, expires at {}", user.userId(),
				token.expiresAt());

		return new B2cLoginResult(token.token(), token.expiresAt(),
				clock.instant().truncatedTo(ChronoUnit.SECONDS), userMapper.resolveSubject(idToken),
				user);
	}

	/**
	 * The provider's authorization endpoint, with our state and nonce. Every part of this url is
	 * configuration - nothing in it comes from the request.
	 */
	private String authorizationUrl(B2cTransaction transaction) {
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
	 * What a completed B2C sign-in produced.
	 *
	 * <p>The provider's ID token is deliberately not a component of this record, so there is no
	 * path by which it could be serialised to a client or written to a log.
	 */
	public record B2cLoginResult(String token, Instant expiresAt, Instant serverTime,
			String b2cSubject, TnfUser user) {
	}
}
