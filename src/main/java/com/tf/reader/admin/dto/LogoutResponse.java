package com.tf.reader.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param sessionRevoked true when this call revoked the session, false when it was already revoked.
 *        Either way the session is not usable afterwards, which is what makes logout idempotent.
 */
@Schema(name = "AdminLogoutResponse", description = "Outcome of revoking the current admin session.")
public record LogoutResponse(boolean sessionRevoked) {
}
