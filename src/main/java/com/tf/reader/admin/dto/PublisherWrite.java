package com.tf.reader.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The PublisherWrite schema. {@code code} is lower-case, digits and hyphens per
 * the contract pattern. The entity normalises it to upper-case on save, but the
 * wire format keeps lower-case.
 */
public record PublisherWrite(
		@NotBlank @Pattern(regexp = "^[a-z0-9-]{2,40}$", message = "code must be lower-case letters, digits and hyphens, 2–40 characters") String code,

		@NotBlank @Size(max = 200) String name,

		@Size(max = 1000) String description,

		String logoUrl) {
}
