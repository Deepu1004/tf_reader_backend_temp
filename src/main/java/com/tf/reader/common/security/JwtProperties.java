package com.tf.reader.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT signing and lifetime configuration, bound from {@code tf.security.jwt.*}.
 *
 * @param secret          HMAC signing secret; must be at least 32 bytes for HS256
 * @param refreshTokenTtl also the absolute lifetime of a session, since rotation inherits the
 *                        original expiry rather than extending it
 */
@ConfigurationProperties(prefix = "tf.security.jwt")
public record JwtProperties(String issuer, String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

	private static final String DEFAULT_ISSUER = "tf-reader";
	private static final Duration DEFAULT_ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

	/** Twelve hours, so one working day needs one sign in. */
	private static final Duration DEFAULT_REFRESH_TOKEN_TTL = Duration.ofHours(12);

	public JwtProperties {
		issuer = (issuer == null || issuer.isBlank()) ? DEFAULT_ISSUER : issuer;
		accessTokenTtl = accessTokenTtl == null ? DEFAULT_ACCESS_TOKEN_TTL : accessTokenTtl;
		refreshTokenTtl = refreshTokenTtl == null ? DEFAULT_REFRESH_TOKEN_TTL : refreshTokenTtl;
	}

}
