package com.tf.reader.admin.repository;

import java.time.Instant;
import java.util.Optional;

import com.tf.reader.admin.entity.AdminSession;

/**
 * Conditional session updates that must be a single atomic database operation. Read-then-write would
 * let two concurrent refreshes with the same token both succeed.
 */
public interface AdminSessionRepositoryCustom {

	/**
	 * Revokes the row holding this token hash, but only if it is not already revoked and not expired.
	 * Winning this update is what earns the right to issue a replacement row, so only one of two
	 * concurrent refreshes with the same token can proceed.
	 *
	 * @return the revoked row, or empty when the token is unknown, already used or expired
	 */
	Optional<AdminSession> revokeForExchange(String refreshTokenHash, String reason, Instant now);

	/**
	 * Marks the session revoked if it is not revoked already. Safe to call repeatedly.
	 *
	 * @return true only when this call performed the revocation
	 */
	boolean revoke(String sessionId, String reason, Instant now);

}
