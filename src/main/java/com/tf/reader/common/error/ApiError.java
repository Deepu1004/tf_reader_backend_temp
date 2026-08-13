package com.tf.reader.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The single error envelope defined by the published API contract.
 *
 * @param timestamp a string, not an {@code Instant}, so that Spring MVC and the filter chain's bare
 *                  {@code ObjectMapper} format it identically
 */
@Schema(name = "Error", description = "Every error in the system uses this shape.")
public record ApiError(

		@Schema(format = "date-time", example = "2026-08-12T14:00:00Z",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String timestamp,

		@Schema(example = "401", requiredMode = Schema.RequiredMode.REQUIRED) int status,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ErrorCode code,

		@Schema(description = "For a human. Do not switch on this, switch on code.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String message,

		@Schema(example = "/api/admin/v1/auth/refresh", requiredMode = Schema.RequiredMode.REQUIRED)
		String path) {
}
