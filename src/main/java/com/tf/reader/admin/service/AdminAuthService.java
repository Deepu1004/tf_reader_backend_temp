package com.tf.reader.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.dto.AdminProfileResponse;
import com.tf.reader.admin.dto.TokenPair;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.exception.InvalidCredentialsException;
import com.tf.reader.admin.exception.InvalidRefreshTokenException;
import com.tf.reader.admin.repository.AdminUserRepository;
import com.tf.reader.admin.service.AdminSessionService.IssuedRefreshToken;
import com.tf.reader.admin.service.AdminTokenService.MintedToken;

/**
 * Orchestrates login, refresh and logout. Access-token minting lives in {@link AdminTokenService} and
 * refresh-token and session state in {@link AdminSessionService}; this only sequences them.
 */
@Service
public class AdminAuthService {

	private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

	private final AdminUserRepository adminUserRepository;
	private final AdminTokenService adminTokenService;
	private final AdminSessionService adminSessionService;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	/** Verified against when no admin matches, so an unknown email costs the same as a known one. */
	private final String timingEqualisationHash;

	public AdminAuthService(AdminUserRepository adminUserRepository, AdminTokenService adminTokenService,
			AdminSessionService adminSessionService, PasswordEncoder passwordEncoder, Clock jwtClock) {

		this.adminUserRepository = adminUserRepository;
		this.adminTokenService = adminTokenService;
		this.adminSessionService = adminSessionService;
		this.passwordEncoder = passwordEncoder;
		this.clock = jwtClock;
		this.timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Verifies credentials and opens a new session. Unknown email, wrong password and a non-active
	 * account are indistinguishable: same exception, and comparable timing because BCrypt runs either
	 * way.
	 */
	public AdminLoginResponse login(String email, String rawPassword) {
		Optional<AdminUser> candidate = this.adminUserRepository.findByEmail(email);

		String storedHash = candidate.map(AdminUser::getPasswordHash)
				.filter(hash -> hash != null && !hash.isBlank())
				.orElse(this.timingEqualisationHash);

		boolean passwordMatches = this.passwordEncoder.matches(rawPassword, storedHash);

		if (candidate.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}

		AdminUser adminUser = candidate.get();
		if (adminUser.getStatus() != AdminStatus.ACTIVE) {
			log.info("Rejected login for admin {} in status {}", adminUser.getId(), adminUser.getStatus());
			throw new InvalidCredentialsException();
		}

		TokenPair tokens = openSession(adminUser);

		adminUser.setLastLoginAt(this.clock.instant());
		this.adminUserRepository.save(adminUser);

		log.info("Admin {} logged in", adminUser.getId());
		return AdminLoginResponse.of(tokens, AdminProfileResponse.from(adminUser));
	}

	/**
	 * Exchanges a refresh token for a new access token and a new refresh token.
	 *
	 * <p>The token is opaque, so it is resolved by looking up its fingerprint rather than by reading
	 * anything out of it. An access token hashes to nothing this store knows, so letting one expire is
	 * never a route to extending it.
	 */
	public TokenPair refresh(String refreshTokenValue) {
		if (isBlank(refreshTokenValue)) {
			throw new InvalidRefreshTokenException("Refresh token was blank");
		}

		AdminSession session = this.adminSessionService.findByRefreshToken(refreshTokenValue)
				.orElseThrow(() -> rejectTokenMatchingNoCurrentSession(refreshTokenValue));

		// Role and scope are re-read here rather than carried in the token, so a change takes effect on
		// the next refresh.
		AdminUser adminUser = this.adminUserRepository.findById(session.getAdminUserId())
				.orElseThrow(() -> revokeAndReject(session.getId(), "Admin no longer exists"));

		if (adminUser.getStatus() != AdminStatus.ACTIVE) {
			throw revokeAndReject(session.getId(), "Admin is in status " + adminUser.getStatus());
		}

		IssuedRefreshToken rotated = this.adminSessionService.rotate(refreshTokenValue)
				.orElseThrow(() -> handleRotationFailure(session.getId()));

		MintedToken accessToken = this.adminTokenService.mintAccessToken(adminUser, session.getId());

		// Time left on the original session, not a fresh TTL: rotation does not move that moment.
		return new TokenPair(accessToken.value(), this.adminTokenService.accessTokenTtlSeconds(),
				rotated.value(), secondsUntil(rotated.session().getExpiresAt()));
	}

	/**
	 * Revokes the session the presented refresh token belongs to. A token matching no session is
	 * accepted silently, so the endpoint cannot be used to discover which sessions are live.
	 */
	public void logout(String refreshTokenValue) {
		if (isBlank(refreshTokenValue)) {
			return;
		}

		// A superseded token still identifies its session, and revoking is the safe direction.
		Optional<AdminSession> session = this.adminSessionService.findByRefreshToken(refreshTokenValue)
				.or(() -> this.adminSessionService.findBySupersededRefreshToken(refreshTokenValue));

		if (session.isEmpty()) {
			log.debug("Logout presented a refresh token that matched no session");
			return;
		}

		String sessionId = session.get().getId();
		if (this.adminSessionService.revoke(sessionId, AdminSessionService.REASON_LOGOUT)) {
			log.info("Session {} revoked by logout", sessionId);
		}
	}

	public AdminUser requireAdmin(String adminUserId) {
		return this.adminUserRepository.findById(adminUserId)
				.orElseThrow(InvalidCredentialsException::new);
	}

	private TokenPair openSession(AdminUser adminUser) {
		String sessionId = this.adminTokenService.newSessionId();
		Instant refreshExpiresAt = this.adminTokenService.refreshTokenExpiryFromNow();

		// The session must exist before the access token is handed out, since every admin request
		// validates against it.
		IssuedRefreshToken refreshToken = this.adminSessionService.createSession(sessionId, adminUser.getId(),
				refreshExpiresAt);

		MintedToken accessToken = this.adminTokenService.mintAccessToken(adminUser, sessionId);

		return new TokenPair(accessToken.value(), this.adminTokenService.accessTokenTtlSeconds(),
				refreshToken.value(), this.adminTokenService.refreshTokenTtlSeconds());
	}

	/**
	 * No live session holds this token as current. If a session rotated away from it, it is a stolen
	 * token being replayed, so the whole session is revoked rather than one request refused.
	 */
	private InvalidRefreshTokenException rejectTokenMatchingNoCurrentSession(String refreshTokenValue) {
		Optional<AdminSession> superseded = this.adminSessionService
				.findBySupersededRefreshToken(refreshTokenValue);

		if (superseded.isEmpty()) {
			return new InvalidRefreshTokenException("Refresh token matches no session");
		}

		String sessionId = superseded.get().getId();
		this.adminSessionService.revoke(sessionId, AdminSessionService.REASON_TOKEN_REUSE);
		log.warn("Superseded refresh token replayed for session {}; session revoked", sessionId);
		return new InvalidRefreshTokenException("Refresh token reuse detected for session " + sessionId);
	}

	/**
	 * The token was current when read but the guarded swap did not match, so decide which precondition
	 * failed. A concurrent refresh having won the race is the same reuse signature.
	 */
	private InvalidRefreshTokenException handleRotationFailure(String sessionId) {
		Optional<AdminSession> session = this.adminSessionService.find(sessionId);

		if (session.isEmpty()) {
			return new InvalidRefreshTokenException("No session " + sessionId);
		}

		AdminSession existing = session.get();
		if (existing.getRevokedAt() != null) {
			return new InvalidRefreshTokenException("Session " + sessionId + " is revoked");
		}
		if (!existing.getExpiresAt().isAfter(this.clock.instant())) {
			return new InvalidRefreshTokenException("Session " + sessionId + " is expired");
		}

		this.adminSessionService.revoke(sessionId, AdminSessionService.REASON_TOKEN_REUSE);
		log.warn("Concurrent use of one refresh token for session {}; session revoked", sessionId);
		return new InvalidRefreshTokenException("Refresh token reuse detected for session " + sessionId);
	}

	private InvalidRefreshTokenException revokeAndReject(String sessionId, String reason) {
		this.adminSessionService.revoke(sessionId, reason);
		return new InvalidRefreshTokenException(reason);
	}

	/** Never negative: an expired session never reaches this point. */
	private long secondsUntil(Instant expiresAt) {
		return Math.max(0, Duration.between(this.clock.instant(), expiresAt).toSeconds());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
