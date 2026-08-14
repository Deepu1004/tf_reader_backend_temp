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
	 * Verifies credentials against the stored BCrypt hash and opens a session. Unknown email, wrong
	 * password and a non-active account are indistinguishable: same exception, and comparable timing
	 * because BCrypt runs either way.
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

		// The row must exist before the access token is handed out, since every admin request
		// validates against it.
		IssuedRefreshToken issued = this.adminSessionService.createSession(adminUser.getId(),
				this.adminTokenService.refreshTokenExpiryFromNow());

		MintedToken accessToken = this.adminTokenService.mintAccessToken(adminUser,
				issued.session().getId());

		adminUser.setLastLoginAt(this.clock.instant());
		this.adminUserRepository.save(adminUser);

		log.info("Admin {} logged in, session {}", adminUser.getId(), issued.session().getId());

		TokenPair tokens = new TokenPair(accessToken.value(), this.adminTokenService.accessTokenTtlSeconds(),
				issued.value(), this.adminTokenService.refreshTokenTtlSeconds());

		return AdminLoginResponse.of(tokens, AdminProfileResponse.from(adminUser));
	}

	/**
	 * Exchanges a refresh token: the row it belongs to is revoked and a fresh row issued in its place.
	 *
	 * <p>The token is opaque, so it is resolved by looking up its fingerprint rather than by reading
	 * anything out of it. An access token hashes to nothing this store knows, so letting one expire is
	 * never a route to extending it.
	 */
	public TokenPair refresh(String refreshTokenValue) {
		if (isBlank(refreshTokenValue)) {
			throw new InvalidRefreshTokenException("Refresh token was blank");
		}

		// Winning this revocation is what earns the right to issue a replacement, so two concurrent
		// refreshes with one token cannot both succeed.
		AdminSession claimed = this.adminSessionService.revokeForExchange(refreshTokenValue)
				.orElseThrow(() -> rejectUnexchangeableToken(refreshTokenValue));

		// Role and scope are re-read here rather than carried in the token, so a change takes effect on
		// the next refresh.
		AdminUser adminUser = this.adminUserRepository.findById(claimed.getAdminUserId())
				.orElseThrow(() -> new InvalidRefreshTokenException(
						"Admin " + claimed.getAdminUserId() + " no longer exists"));

		if (adminUser.getStatus() != AdminStatus.ACTIVE) {
			throw new InvalidRefreshTokenException("Admin is in status " + adminUser.getStatus());
		}

		// The replacement inherits the original expiry, so activity never extends the session.
		IssuedRefreshToken issued = this.adminSessionService.createSession(adminUser.getId(),
				claimed.getExpiresAt());

		MintedToken accessToken = this.adminTokenService.mintAccessToken(adminUser,
				issued.session().getId());

		return new TokenPair(accessToken.value(), this.adminTokenService.accessTokenTtlSeconds(),
				issued.value(), secondsUntil(claimed.getExpiresAt()));
	}

	/**
	 * Revokes the session the presented refresh token belongs to. A token matching no row is accepted
	 * silently, so the endpoint cannot be used to discover which sessions are live.
	 */
	public void logout(String refreshTokenValue) {
		if (isBlank(refreshTokenValue)) {
			return;
		}

		Optional<AdminSession> session = this.adminSessionService.findByRefreshToken(refreshTokenValue);

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

	/**
	 * The row could not be claimed. Unknown, already exchanged, revoked and expired are all the same
	 * 401 to the caller; only the server log distinguishes them. Nothing else is revoked: a replayed
	 * token affects its own row and no other session belonging to the admin.
	 */
	private InvalidRefreshTokenException rejectUnexchangeableToken(String refreshTokenValue) {
		Optional<AdminSession> existing = this.adminSessionService.findByRefreshToken(refreshTokenValue);

		if (existing.isEmpty()) {
			return new InvalidRefreshTokenException("Refresh token matches no session");
		}

		AdminSession session = existing.get();
		if (session.getRevokedAt() != null) {
			log.warn("Refresh token for session {} was presented again after being {}", session.getId(),
					session.getRevokedReason());
			return new InvalidRefreshTokenException("Refresh token for session " + session.getId()
					+ " was already used or revoked");
		}
		return new InvalidRefreshTokenException("Session " + session.getId() + " is expired");
	}

	/** Never negative: an expired session never reaches this point. */
	private long secondsUntil(Instant expiresAt) {
		return Math.max(0, Duration.between(this.clock.instant(), expiresAt).toSeconds());
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
