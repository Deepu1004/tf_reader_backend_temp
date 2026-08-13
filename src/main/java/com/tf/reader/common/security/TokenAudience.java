package com.tf.reader.common.security;

/**
 * The three token audiences issued and accepted by this service.
 *
 * <p>Each audience has its own {@link org.springframework.security.oauth2.jwt.JwtDecoder} and is
 * validated exactly, so a token minted for one audience can never be replayed against another.
 */
public final class TokenAudience {

	/** Admin API access tokens. */
	public static final String ADMIN = "tf-admin";

	/** Reader app API access tokens. */
	public static final String APP = "tf-app";

	/** Refresh tokens. Only ever accepted by the refresh endpoint. */
	public static final String REFRESH = "tf-refresh";

	private TokenAudience() {
	}

}
