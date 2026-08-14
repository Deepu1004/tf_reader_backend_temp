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
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.repository.AdminSessionRepository;

/**
 * Owns the lifecycle of {@link AdminSession} and of the opaque refresh token itself: one row per sign
 * in, revoked and replaced on refresh, revoked on logout.
 *
 * <p>The raw token exists only in the response that hands it out. Only a SHA-256 fingerprint is
 * stored, so a leaked database dump yields no usable session. A plain hash suffices here, unlike for
 * passwords, because the token is 256 bits of {@link SecureRandom} rather than a guessable secret.
 */
@Service
public class AdminSessionService {

	public static final String REASON_LOGOUT = "LOGOUT";
	public static final String REASON_ROTATED = "ROTATED";

	/** The contract prefixes an admin session id with {@code sess_}. */
	static final String SESSION_ID_PREFIX = "sess_";

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
	 * A refresh token and the row it belongs to. The value is returned to the caller once and is not
	 * recoverable from the row afterwards.
	 */
	public record IssuedRefreshToken(String value, AdminSession session) {
	}

	/**
	 * Inserts a new session row.
	 *
	 * @param expiresAt when the session dies, absolutely. A replacement row issued on refresh inherits
	 *                  the original value rather than getting a fresh one.
	 */
	public IssuedRefreshToken createSession(String adminUserId, Instant expiresAt) {
		String tokenValue = newRefreshTokenValue();

		AdminSession session = new AdminSession();
		session.setId(newSessionId());
		session.setAdminUserId(adminUserId);
		session.setRefreshTokenHash(fingerprint(tokenValue));
		session.setIssuedAt(this.clock.instant());
		session.setExpiresAt(expiresAt);

		return new IssuedRefreshToken(tokenValue, this.adminSessionRepository.save(session));
	}

	/**
	 * Claims the row this token belongs to by revoking it, which is what earns the right to issue a
	 * replacement.
	 *
	 * @return the row as it was before revocation, or empty when the token is unknown, already used or
	 *         expired
	 */
	public Optional<AdminSession> revokeForExchange(String presentedTokenValue) {
		return this.adminSessionRepository.revokeForExchange(fingerprint(presentedTokenValue),
				REASON_ROTATED, this.clock.instant());
	}

	public Optional<AdminSession> findByRefreshToken(String tokenValue) {
		return this.adminSessionRepository.findByRefreshTokenHash(fingerprint(tokenValue));
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

	private static String newSessionId() {
		return SESSION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
	}

	private String newRefreshTokenValue() {
		byte[] tokenBytes = new byte[REFRESH_TOKEN_BYTES];
		this.secureRandom.nextBytes(tokenBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
	}

	/** SHA-256, lowercase hex, as the contract specifies for {@code refreshTokenHash}. */
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
