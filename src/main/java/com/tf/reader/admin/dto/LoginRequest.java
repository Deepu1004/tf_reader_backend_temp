package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AdminLoginRequest", description = "Admin email and password.")
public record LoginRequest(

		@Schema(example = "super.admin@tf-reader.local", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank @Email String email,

		@Schema(format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank String password) {
}
