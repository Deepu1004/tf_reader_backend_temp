package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tokens issued by login and by refresh.
 *
 * @param accessToken  short-lived {@code tf-admin} token for the Authorization header
 * @param refreshToken longer-lived {@code tf-refresh} token, only ever sent to the refresh endpoint
 * @param tokenType    always {@code Bearer}
 * @param expiresIn    access-token lifetime in seconds
 */
@Schema(name = "AdminTokenResponse", description = "Newly issued admin tokens.")
public record TokenResponse(

		@Schema(description = "JWT with aud=tf-admin. Send as: Authorization: Bearer <accessToken>")
		String accessToken,

		@Schema(description = "JWT with aud=tf-refresh. Rotated on every use; the previous one stops working.")
		String refreshToken,

		@Schema(example = "Bearer") String tokenType,

		@Schema(description = "Access-token lifetime in seconds.", example = "900") long expiresIn) {

	public static final String BEARER = "Bearer";

	public static TokenResponse bearer(String accessToken, String refreshToken, long expiresInSeconds) {
		return new TokenResponse(accessToken, refreshToken, BEARER, expiresInSeconds);
	}

}
