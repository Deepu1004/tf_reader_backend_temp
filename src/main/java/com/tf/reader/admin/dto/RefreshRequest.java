package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The body form of the refresh token, for a caller with no cookie jar.
 *
 * <p>Nothing here is validated. The browser path sends no body at all, so a missing or blank token is
 * not a malformed request, it is a caller presenting no credential. That is a 401 from the service on
 * refresh and a 204 on logout, which is what the contract says. A {@code @NotBlank} here would turn
 * both into a 400 and break the console's restore on page load.
 */
@Schema(name = "RefreshRequest",
		description = "The refresh token previously issued to this client. Ignored when the adminRefresh "
				+ "cookie is present.")
public record RefreshRequest(

		@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
		String refreshToken) {
}
