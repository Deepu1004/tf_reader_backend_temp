package com.tf.reader.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response body for {@code POST /api/v1/auth/token} and {@code POST /api/v1/auth/refresh}.
 *
 * @param expiresIn seconds until {@code accessToken} expires, not an absolute instant - matching
 *                   what a client needs to schedule its own silent refresh
 * @param deviceId  an institutional SAML device's own id, present only on that path and omitted
 *                   entirely rather than sent as null everywhere else. The client persists it
 *                   locally and echoes it back on {@code /auth/saml/start} next time, which is
 *                   what lets the same device reclaim its seat and its loans/shelf instead of
 *                   minting a new, disconnected one. Never a username or email - there is no
 *                   such thing behind this id.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String accessToken, String refreshToken, long expiresIn, String deviceId) {

	public TokenResponse(String accessToken, String refreshToken, long expiresIn) {
		this(accessToken, refreshToken, expiresIn, null);
	}
}
