package com.tf.reader.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.TokenResponse;
import com.tf.reader.admin.entity.AdminSession;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.exception.InvalidCredentialsException;
import com.tf.reader.admin.exception.InvalidRefreshTokenException;
import com.tf.reader.admin.repository.AdminUserRepository;
import com.tf.reader.admin.service.AdminTokenService.MintedToken;
import com.tf.reader.common.security.JwtConfig;
import com.tf.reader.common.security.TokenClaims;

/**
 * Orchestrates login, refresh and logout.
 *
 * <p>Deliberately thin: token minting lives in {@link AdminTokenService}, session state in
 * {@link AdminSessionService}, token validation in the decoders. This class only sequences them and
 * decides what constitutes a failure.
 */
@Service
public class AdminAuthService {

	private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

	private final AdminUserRepository adminUserRepository;
	private final AdminTokenService adminTokenService;
	private final AdminSessionService adminSessionService;
	private final PasswordEncoder passwordEncoder;
	private final JwtDecoder refreshTokenDecoder;
	private final Clock clock;

	/**
	 * Hashed once at startup and verified against when no admin matches the email, so that a login
	 * for an unknown address costs the same as one for a known address.
	 */
	private final String timingEqualisationHash;

	public AdminAuthService(AdminUserRepository adminUserRepository, AdminTokenService adminTokenService,
			AdminSessionService adminSessionService, PasswordEncoder passwordEncoder,
			@Qualifier(JwtConfig.REFRESH_TOKEN_DECODER) JwtDecoder refreshTokenDecoder, Clock jwtClock) {

		this.adminUserRepository = adminUserRepository;
		this.adminTokenService = adminTokenService;
		this.adminSessionService = adminSessionService;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenDecoder = refreshTokenDecoder;
		this.clock = jwtClock;
		this.timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Verifies credentials and opens a new session.
	 *
	 * <p>Unknown email, wrong password and a non-active account are indistinguishable to the caller:
	 * same exception, same message, and comparable timing because BCrypt runs either way.
	 */
	public TokenResponse login(String email, String rawPassword) {
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

		TokenResponse tokens = openSession(adminUser);

		adminUser.setLastLoginAt(this.clock.instant());
		this.adminUserRepository.save(adminUser);

		log.info("Admin {} logged in", adminUser.getId());
		return tokens;
	}

	/**
	 * Exchanges a refresh token for a new access token and a new refresh token.
	 *
	 * <p>An access token is rejected here regardless of its state, because the refresh decoder only
	 * accepts {@code aud=tf-refresh} with {@code token_use=refresh}. Expiring an access token
	 * therefore never becomes a route to extending it.
	 */
	public TokenResponse refresh(String refreshTokenValue) {
		Jwt refreshToken = decodeRefreshToken(refreshTokenValue);

		String sessionId = refreshToken.getClaimAsString(TokenClaims.SESSION_ID);
		String presentedJti = refreshToken.getId();
		String adminUserId = refreshToken.getSubject();

		if (isBlank(sessionId) || isBlank(presentedJti) || isBlank(adminUserId)) {
			throw new InvalidRefreshTokenException("Refresh token is missing sid, jti or sub");
		}

		AdminUser adminUser = this.adminUserRepository.findById(adminUserId)
				.orElseThrow(() -> revokeAndReject(sessionId, "Admin no longer exists"));

		if (adminUser.getStatus() != AdminStatus.ACTIVE) {
			throw revokeAndReject(sessionId, "Admin is in status " + adminUser.getStatus());
		}

		// A rotated refresh token never outlives the session it belongs to, so the session has an
		// absolute lifetime: activity does not extend it and re-authentication is eventually forced.
		Instant sessionExpiresAt = refreshToken.getExpiresAt();
		MintedToken newRefreshToken = this.adminTokenService.mintRefreshToken(adminUserId, sessionId,
				sessionExpiresAt);

		Optional<AdminSession> rotated = this.adminSessionService.rotate(sessionId, presentedJti, refreshTokenValue,
				newRefreshToken.jti(), newRefreshToken.value());

		if (rotated.isEmpty()) {
			throw handleRotationFailure(sessionId, presentedJti);
		}

		MintedToken accessToken = this.adminTokenService.mintAccessToken(adminUser, sessionId);
		return TokenResponse.bearer(accessToken.value(), newRefreshToken.value(),
				this.adminTokenService.accessTokenTtlSeconds());
	}

	/**
	 * Revokes the session behind the presented access token.
	 *
	 * <p>Both token types die with it: the refresh token because rotation requires a live session,
	 * and the access token because {@link com.tf.reader.admin.security.ActiveSessionValidator}
	 * re-checks the session on every request.
	 *
	 * @return false when the session was already revoked; the outcome is the same either way
	 */
	public boolean logout(String sessionId) {
		if (isBlank(sessionId)) {
			return false;
		}
		boolean revokedNow = this.adminSessionService.revoke(sessionId, AdminSessionService.REASON_LOGOUT);
		if (revokedNow) {
			log.info("Session {} revoked by logout", sessionId);
		}
		return revokedNow;
	}

	public AdminUser requireAdmin(String adminUserId) {
		return this.adminUserRepository.findById(adminUserId)
				.orElseThrow(InvalidCredentialsException::new);
	}

	private TokenResponse openSession(AdminUser adminUser) {
		String sessionId = this.adminTokenService.newSessionId();
		Instant refreshExpiresAt = this.adminTokenService.refreshTokenExpiryFromNow();

		MintedToken refreshToken = this.adminTokenService.mintRefreshToken(adminUser.getId(), sessionId,
				refreshExpiresAt);

		// The session must exist before the access token is handed out, since every admin request
		// validates against it.
		this.adminSessionService.createSession(sessionId, adminUser.getId(), refreshToken.jti(),
				refreshToken.value(), refreshExpiresAt);

		MintedToken accessToken = this.adminTokenService.mintAccessToken(adminUser, sessionId);

		return TokenResponse.bearer(accessToken.value(), refreshToken.value(),
				this.adminTokenService.accessTokenTtlSeconds());
	}

	private Jwt decodeRefreshToken(String refreshTokenValue) {
		try {
			return this.refreshTokenDecoder.decode(refreshTokenValue);
		}
		catch (JwtException ex) {
			// Signature, issuer, audience, token_use and expiry failures all land here.
			throw new InvalidRefreshTokenException("Refresh token failed validation", ex);
		}
	}

	/**
	 * Decides why rotation did not match and reacts.
	 *
	 * <p>If the session is live but the presented {@code jti} is no longer current, the token has
	 * already been exchanged. That is the signature of a stolen-and-replayed refresh token, so the
	 * whole session is revoked rather than only the request refused.
	 */
	private InvalidRefreshTokenException handleRotationFailure(String sessionId, String presentedJti) {
		Optional<AdminSession> session = this.adminSessionService.find(sessionId);

		if (session.isEmpty()) {
			return new InvalidRefreshTokenException("No session " + sessionId);
		}

		AdminSession existing = session.get();
		if (existing.getRevokedAt() != null) {
			return new InvalidRefreshTokenException("Session " + sessionId + " is revoked");
		}
		if (!presentedJti.equals(existing.getCurrentRefreshJti())) {
			this.adminSessionService.revoke(sessionId, AdminSessionService.REASON_TOKEN_REUSE);
			log.warn("Superseded refresh token replayed for session {}; session revoked", sessionId);
			return new InvalidRefreshTokenException("Refresh token reuse detected for session " + sessionId);
		}
		return new InvalidRefreshTokenException("Session " + sessionId + " is expired or its token did not match");
	}

	private InvalidRefreshTokenException revokeAndReject(String sessionId, String reason) {
		this.adminSessionService.revoke(sessionId, reason);
		return new InvalidRefreshTokenException(reason);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
