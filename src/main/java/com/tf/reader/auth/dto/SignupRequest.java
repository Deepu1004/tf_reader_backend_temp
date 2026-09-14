package com.tf.reader.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/v1/auth/signup}: a new individual reader's own credentials. */
public record SignupRequest(

		@NotBlank(message = "is required")
		@Email(message = "must be a valid email address")
		String email,

		@NotBlank(message = "is required")
		@Size(min = 8, message = "must be at least 8 characters")
		String password) {
}
