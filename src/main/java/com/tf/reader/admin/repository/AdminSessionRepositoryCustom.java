package com.tf.reader.admin.repository;

import java.time.Instant;
import java.util.Optional;

import com.tf.reader.admin.entity.AdminSession;

/**
 * Conditional session updates that must be a single atomic database operation.
 *
 * <p>Read-then-write would leave a window in which two concurrent refreshes with the same token
 * both succeed, so these are expressed as guarded {@code findAndModify}/{@code updateFirst} calls.
 */
public interface AdminSessionRepositoryCustom {

	/**
	 * Atomically swaps the session's current refresh token, but only if the presented token is
	 * still the current one and the session is neither revoked nor expired.
	 *
	 * @return the rotated session, or empty when the guard did not match. Empty means the caller
	 *         must treat the presented token as invalid; it may also indicate replay of a
	 *         superseded token.
	 */
	Optional<AdminSession> rotateRefreshToken(String sessionId, String expectedJti, String expectedTokenHash,
			String newJti, String newTokenHash, Instant now);

	/**
	 * Marks the session revoked if it is not revoked already.
	 *
	 * @return true when this call performed the revocation, false when it was already revoked or
	 *         no such session exists. Safe to call repeatedly.
	 */
	boolean revoke(String sessionId, String reason, Instant now);

}
