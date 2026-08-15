package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The whole body of a refresh response; login returns these four fields plus the signed-in admin.
 *
 * @param refreshExpiresIn seconds until the session's absolute expiry, so on a refresh this is the
 *                         time left on the original session rather than a fresh twelve hours
 */
@Schema(name = "TokenPair", description = "Two tokens with different jobs.")
public record TokenPair(

		@Schema(description = "JWT with aud=tf-admin. Send as: Authorization: Bearer <accessToken>",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String accessToken,

		@Schema(description = "Seconds. Short on purpose, because nothing can revoke it early.",
				example = "900", requiredMode = Schema.RequiredMode.REQUIRED)
		long expiresIn,

		@Schema(description = "Opaque, not a JWT, so there is nothing to read out of it. Rotated on every "
				+ "use; the previous one stops working. Never send it in an Authorization header.",
				example = "8Kd2mXqR7vT1nP4wZ0aB3cE6gH9jL5sY",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String refreshToken,

		@Schema(description = "Seconds. Twelve hours from sign in, so one working day needs one sign in.",
				example = "43200", requiredMode = Schema.RequiredMode.REQUIRED)
		long refreshExpiresIn) {
}
