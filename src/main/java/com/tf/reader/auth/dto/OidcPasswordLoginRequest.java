package com.tf.reader.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/oidc/start}: the reader's own credentials. */
public record OidcPasswordLoginRequest(

		@NotBlank(message = "is required")
		String username,

		@NotBlank(message = "is required")
		String password) {
}
