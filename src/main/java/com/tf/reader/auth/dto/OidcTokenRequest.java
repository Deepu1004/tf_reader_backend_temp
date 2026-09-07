package com.tf.reader.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/v1/auth/oidc/token}: the one-time id from {@code /oidc/start}. */
public record OidcTokenRequest(

		@NotBlank(message = "is required")
		String oidcTxnId) {
}
