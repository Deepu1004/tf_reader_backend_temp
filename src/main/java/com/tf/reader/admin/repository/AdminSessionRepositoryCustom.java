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
	 * Swaps the current refresh-token hash only if the presented one is still current and the session
	 * is neither revoked nor expired, recording the presented hash as superseded.
	 *
	 * @return empty when the guard did not match, which may also mean a superseded token was replayed
	 */
	Optional<AdminSession> rotateRefreshToken(String presentedTokenHash, String newTokenHash, Instant now);

	/**
	 * Marks the session revoked if it is not revoked already. Safe to call repeatedly.
	 *
	 * @return true only when this call performed the revocation
	 */
	boolean revoke(String sessionId, String reason, Instant now);

}
