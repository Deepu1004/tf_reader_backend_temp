package com.tf.reader.admin.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.common.security.JwtProperties;
import com.tf.reader.common.security.TokenAudience;
import com.tf.reader.common.security.TokenClaims;

/**
 * Mints admin JWTs. Nothing here reads or writes session state, and nothing here validates tokens;
 * validation lives entirely in the decoders.
 */
@Service
public class AdminTokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;
	private final Clock clock;

	public AdminTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock jwtClock) {
		this.jwtEncoder = jwtEncoder;
		this.jwtProperties = jwtProperties;
		this.clock = jwtClock;
	}

	/**
	 * @param value     the encoded JWT
	 * @param jti       its unique identifier
	 * @param expiresAt its expiry
	 */
	public record MintedToken(String value, String jti, Instant expiresAt) {
	}

	/**
	 * Access token for the given admin and session.
	 *
	 * <p>Scope claims are written only when the admin actually carries that scope. An absent claim
	 * means "no scope", never "all scopes".
	 */
	public MintedToken mintAccessToken(AdminUser adminUser, String sessionId) {
		Instant issuedAt = this.clock.instant();
		Instant expiresAt = issuedAt.plus(this.jwtProperties.accessTokenTtl());
		String jti = newId();

		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(this.jwtProperties.issuer())
				.subject(adminUser.getId())
				.audience(List.of(TokenAudience.ADMIN))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.id(jti)
				.claim(TokenClaims.TOKEN_USE, TokenClaims.USE_ACCESS)
				.claim(TokenClaims.SESSION_ID, sessionId)
				.claim(TokenClaims.ROLE, adminUser.getRole().name());

		if (hasText(adminUser.getPublisherId())) {
			claims.claim(TokenClaims.SCOPE_PUBLISHER_ID, adminUser.getPublisherId());
		}
		if (hasText(adminUser.getInstitutionId())) {
			claims.claim(TokenClaims.SCOPE_INSTITUTION_ID, adminUser.getInstitutionId());
		}

		return new MintedToken(encode(claims.build()), jti, expiresAt);
	}

	/**
	 * Refresh token for the given session.
	 *
	 * <p>Carries no role or scope: it authorizes nothing by itself and is only ever exchanged, so
	 * the admin's current role and scope are re-read from the database on every refresh.
	 */
	public MintedToken mintRefreshToken(String adminUserId, String sessionId, Instant expiresAt) {
		Instant issuedAt = this.clock.instant();
		String jti = newId();

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(this.jwtProperties.issuer())
				.subject(adminUserId)
				.audience(List.of(TokenAudience.REFRESH))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.id(jti)
				.claim(TokenClaims.TOKEN_USE, TokenClaims.USE_REFRESH)
				.claim(TokenClaims.SESSION_ID, sessionId)
				.build();

		return new MintedToken(encode(claims), jti, expiresAt);
	}

	public Instant refreshTokenExpiryFromNow() {
		return this.clock.instant().plus(this.jwtProperties.refreshTokenTtl());
	}

	public long accessTokenTtlSeconds() {
		return this.jwtProperties.accessTokenTtl().toSeconds();
	}

	public String newSessionId() {
		return newId();
	}

	private String encode(JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return this.jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private static String newId() {
		return UUID.randomUUID().toString();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
