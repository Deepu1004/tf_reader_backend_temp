package com.tf.reader.auth.oidc.validation;

import java.time.Clock;
import java.util.List;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.tf.reader.auth.b2c.B2cProperties;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Verifies a B2C ID token: <b>signature, issuer, audience, expiry</b>.
 *
 * <p>The individual-flow counterpart of {@link OidcIdTokenDecoder} - identical checks, against
 * {@link B2cProperties} instead of {@code OidcProperties}. It lives in this package rather than
 * in {@code auth.b2c}, alongside its sibling, for the reason {@link OidcIdTokenDecoder}'s own
 * javadoc gives: {@code SecurityArchitectureTest} restricts naming a {@code JwtDecoder} to four
 * specific packages, and this is one of them.
 */
@Component
public class B2cIdTokenDecoder {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(B2cIdTokenDecoder.class);

	private final JwtDecoder decoder;
	private final String expectedIssuer;
	private final String expectedAudience;

	public B2cIdTokenDecoder(B2cProperties properties, Clock clock) {
		this.expectedIssuer = properties.issuer();
		this.expectedAudience = properties.clientId();

		NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.build();

		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);

		nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
				new JwtIssuerValidator(this.expectedIssuer),
				timestamps,
				audienceValidator(this.expectedAudience))));

		this.decoder = nimbus;
	}

	/**
	 * @param idToken the raw ID token as it came back from the token endpoint
	 * @return the verified token, whose claims are now safe to read
	 * @throws ApiException 401 if any check fails
	 */
	public Jwt verify(String idToken) {
		try {
			Jwt verified = this.decoder.decode(idToken);
			log.debug("B2C ID token verified: signature, issuer, audience and expiry all passed");
			return verified;
		}
		catch (JwtException rejected) {
			log.warn("B2C ID token rejected: {}", rejected.getMessage());
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The identity provider's token could not be validated.");
		}
	}

	/** The expected {@code iss}, for anything that wants to report the configuration. */
	public String expectedIssuer() {
		return this.expectedIssuer;
	}

	private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
		return token -> {
			List<String> audience = token.getAudience();
			if (audience != null && audience.contains(clientId)) {
				return OAuth2TokenValidatorResult.success();
			}
			return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
					"The " + JwtClaimNames.AUD + " claim does not contain this application.", null));
		};
	}
}
