package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A successful login: the token pair, plus who just signed in. The token fields are flattened rather
 * than nested because the contract composes this shape from {@code TokenPair} with {@code allOf}.
 */
@Schema(name = "AdminLoginResponse", description = "Newly issued admin tokens and the signed-in admin.")
public record AdminLoginResponse(

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,

		@Schema(example = "900", requiredMode = Schema.RequiredMode.REQUIRED) long expiresIn,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,

		@Schema(example = "43200", requiredMode = Schema.RequiredMode.REQUIRED) long refreshExpiresIn,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminProfileResponse user) {

	public static AdminLoginResponse of(TokenPair tokens, AdminProfileResponse user) {
		return new AdminLoginResponse(tokens.accessToken(), tokens.expiresIn(), tokens.refreshToken(),
				tokens.refreshExpiresIn(), user);
	}

}
