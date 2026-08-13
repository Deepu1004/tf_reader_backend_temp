package com.tf.reader.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.tf.reader.admin.repository.AdminSessionRepository;
import com.tf.reader.admin.security.ActiveSessionValidator;
import com.tf.reader.admin.security.AdminRoles;

/**
 * JWT signing and verification.
 *
 * <p>HS256 is the right fit here: this service is both the only issuer and the only verifier of
 * these tokens, so a symmetric key avoids key distribution without giving anything up. Moving to an
 * asymmetric key only becomes worthwhile once a separate service must verify tokens independently.
 *
 * <p>There is one decoder per audience and no permissive decoder. Each rejects, in addition to a
 * bad signature, any token with the wrong issuer, wrong audience, wrong intent or a stale
 * timestamp.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	/** HS256 requires a key of at least the hash length. Anything shorter is rejected outright. */
	private static final int MINIMUM_SECRET_BYTES = 32;

	public static final String ADMIN_ACCESS_TOKEN_DECODER = "adminAccessTokenDecoder";
	public static final String APP_ACCESS_TOKEN_DECODER = "appAccessTokenDecoder";
	public static final String REFRESH_TOKEN_DECODER = "refreshTokenDecoder";

	private final JwtProperties jwtProperties;

	public JwtConfig(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
	}

	@Bean
	Clock jwtClock() {
		return Clock.systemUTC();
	}

	@Bean
	SecretKey jwtSigningKey() {
		String secret = this.jwtProperties.secret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"tf.security.jwt.secret is not configured. Set the TF_JWT_SECRET environment variable "
							+ "to a value of at least " + MINIMUM_SECRET_BYTES + " bytes.");
		}
		byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (keyBytes.length < MINIMUM_SECRET_BYTES) {
			throw new IllegalStateException("tf.security.jwt.secret must be at least " + MINIMUM_SECRET_BYTES
					+ " bytes for HS256 but was " + keyBytes.length + " bytes.");
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
	}

	/**
	 * Admin access tokens. Additionally requires a role claim that maps to a known
	 * {@link com.tf.reader.admin.entity.AdminRole}, and a session that is still active.
	 */
	@Bean(ADMIN_ACCESS_TOKEN_DECODER)
	JwtDecoder adminAccessTokenDecoder(SecretKey jwtSigningKey,
			@Lazy AdminSessionRepository adminSessionRepository, Clock jwtClock) {

		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>(
				baseValidators(TokenAudience.ADMIN, TokenClaims.USE_ACCESS));
		validators.add(new JwtClaimValidator<String>(TokenClaims.ROLE, AdminRoles::isValid));
		validators.add(new ActiveSessionValidator(adminSessionRepository, jwtClock));

		return decoder(jwtSigningKey, validators);
	}

	/**
	 * Reader app access tokens. Present so that the app surface is validated against its own
	 * audience from the outset; an admin token must never authenticate an app request.
	 */
	@Bean(APP_ACCESS_TOKEN_DECODER)
	JwtDecoder appAccessTokenDecoder(SecretKey jwtSigningKey) {
		return decoder(jwtSigningKey, baseValidators(TokenAudience.APP, TokenClaims.USE_ACCESS));
	}

	/**
	 * Refresh tokens. Used only by the refresh endpoint, against a token supplied in the request
	 * body, so a refresh token can never authenticate an API call.
	 */
	@Bean(REFRESH_TOKEN_DECODER)
	JwtDecoder refreshTokenDecoder(SecretKey jwtSigningKey) {
		return decoder(jwtSigningKey, baseValidators(TokenAudience.REFRESH, TokenClaims.USE_REFRESH));
	}

	private List<OAuth2TokenValidator<Jwt>> baseValidators(String audience, String tokenUse) {
		List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
		validators.add(new JwtTimestampValidator());
		validators.add(new JwtIssuerValidator(this.jwtProperties.issuer()));
		validators.add(new ExactAudienceValidator(audience));
		validators.add(new TokenUseValidator(tokenUse));
		return validators;
	}

	private static JwtDecoder decoder(SecretKey signingKey, List<OAuth2TokenValidator<Jwt>> validators) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
		return decoder;
	}

}
