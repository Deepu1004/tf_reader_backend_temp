package com.tf.reader.admin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.repository.AdminSessionRepository;

/**
 * Owns the lifecycle of {@link AdminSession}: create on login, rotate on refresh, revoke on logout.
 *
 * <p>Refresh tokens are stored only as a SHA-256 fingerprint. A plain hash is the right primitive
 * here, unlike for passwords: the token is 128 bits of server-generated randomness inside a signed
 * JWT, so it is not guessable and needs no key stretching.
 */
@Service
public class AdminSessionService {

	public static final String REASON_LOGOUT = "LOGOUT";
	public static final String REASON_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";

	private final AdminSessionRepository adminSessionRepository;
	private final Clock clock;

	public AdminSessionService(AdminSessionRepository adminSessionRepository, Clock jwtClock) {
		this.adminSessionRepository = adminSessionRepository;
		this.clock = jwtClock;
	}

	public AdminSession createSession(String sessionId, String adminUserId, String refreshJti,
			String refreshTokenValue, Instant expiresAt) {

		Instant now = this.clock.instant();
		AdminSession session = new AdminSession();
		session.setId(sessionId);
		session.setAdminUserId(adminUserId);
		session.setCurrentRefreshJti(refreshJti);
		session.setCurrentRefreshTokenHash(fingerprint(refreshTokenValue));
		session.setIssuedAt(now);
		session.setLastRotatedAt(now);
		session.setExpiresAt(expiresAt);
		return this.adminSessionRepository.save(session);
	}

	/**
	 * Atomically swaps the session's refresh token.
	 *
	 * @return the rotated session, or empty when the presented token is not the current one, or the
	 *         session is revoked, expired or unknown
	 */
	public Optional<AdminSession> rotate(String sessionId, String presentedJti, String presentedTokenValue,
			String newJti, String newTokenValue) {

		return this.adminSessionRepository.rotateRefreshToken(sessionId, presentedJti,
				fingerprint(presentedTokenValue), newJti, fingerprint(newTokenValue), this.clock.instant());
	}

	/** @return true if this call revoked the session; false if it was already revoked or unknown. */
	public boolean revoke(String sessionId, String reason) {
		return this.adminSessionRepository.revoke(sessionId, reason, this.clock.instant());
	}

	public Optional<AdminSession> find(String sessionId) {
		return this.adminSessionRepository.findById(sessionId);
	}

	public boolean isActive(String sessionId) {
		return this.adminSessionRepository.existsByIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId,
				this.clock.instant());
	}

	static String fingerprint(String tokenValue) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(tokenValue.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required but unavailable", ex);
		}
	}

}
