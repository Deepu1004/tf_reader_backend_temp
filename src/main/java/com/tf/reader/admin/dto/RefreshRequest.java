package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AdminRefreshRequest", description = "The refresh token previously issued to this client.")
public record RefreshRequest(

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String refreshToken) {
}
