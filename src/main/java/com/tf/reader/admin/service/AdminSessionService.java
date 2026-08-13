package com.tf.reader.admin.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.repository.AdminSessionRepository;

/**
 * Owns the lifecycle of {@link AdminSession} and of the opaque refresh token itself: mint on login,
 * rotate on refresh, revoke on logout.
 *
 * <p>The raw token exists only in the response that hands it out. Only a SHA-256 fingerprint is
 * stored, so a leaked database dump yields no usable session. A plain hash suffices here, unlike for
 * passwords, because the token is 256 bits of {@link SecureRandom} rather than a guessable secret.
 */
@Service
public class AdminSessionService {

	public static final String REASON_LOGOUT = "LOGOUT";
	public static final String REASON_TOKEN_REUSE = "REFRESH_TOKEN_REUSE";

	/** 256 bits, so the token is not guessable and needs no key stretching. */
	private static final int REFRESH_TOKEN_BYTES = 32;

	private final AdminSessionRepository adminSessionRepository;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public AdminSessionService(AdminSessionRepository adminSessionRepository, Clock jwtClock) {
		this.adminSessionRepository = adminSessionRepository;
		this.clock = jwtClock;
	}

	/**
	 * A refresh token and the session it belongs to. The value is returned to the caller once and is
	 * not recoverable from the session afterwards.
	 */
	public record IssuedRefreshToken(String value, AdminSession session) {
	}

	public IssuedRefreshToken createSession(String sessionId, String adminUserId, Instant expiresAt) {
		String tokenValue = newRefreshTokenValue();
		Instant now = this.clock.instant();

		AdminSession session = new AdminSession();
		session.setId(sessionId);
		session.setAdminUserId(adminUserId);
		session.setCurrentRefreshTokenHash(fingerprint(tokenValue));
		session.setIssuedAt(now);
		session.setLastRotatedAt(now);
		session.setExpiresAt(expiresAt);

		return new IssuedRefreshToken(tokenValue, this.adminSessionRepository.save(session));
	}

	/**
	 * Atomically issues a replacement token for the session the presented one belongs to. Never
	 * touches {@code expiresAt}, so a session cannot be extended by refreshing.
	 *
	 * @return empty when the presented token is not the current one, or the session is revoked or
	 *         expired
	 */
	public Optional<IssuedRefreshToken> rotate(String presentedTokenValue) {
		String newTokenValue = newRefreshTokenValue();

		return this.adminSessionRepository
				.rotateRefreshToken(fingerprint(presentedTokenValue), fingerprint(newTokenValue),
						this.clock.instant())
				.map(rotated -> new IssuedRefreshToken(newTokenValue, rotated));
	}

	public Optional<AdminSession> findByRefreshToken(String tokenValue) {
		return this.adminSessionRepository.findByCurrentRefreshTokenHash(fingerprint(tokenValue));
	}

	/** Matches a token this session has already rotated away from, which means it was replayed. */
	public Optional<AdminSession> findBySupersededRefreshToken(String tokenValue) {
		return this.adminSessionRepository.findBySupersededRefreshTokenHashesContaining(fingerprint(tokenValue));
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

	private String newRefreshTokenValue() {
		byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
		this.secureRandom.nextBytes(tokenBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
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
