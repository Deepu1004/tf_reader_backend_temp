package com.tf.reader.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/login}: an individual reader's own credentials. */
public record LoginRequest(

		@NotBlank(message = "is required")
		String email,

		@NotBlank(message = "is required")
		String password) {
}
