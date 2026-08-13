package com.tf.reader.admin.security;

import java.time.Clock;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.admin.repository.AdminSessionRepository;
import com.tf.reader.common.security.TokenClaims;

/**
 * Rejects admin access tokens whose session has been revoked.
 *
 * <p>This is what makes logout take effect immediately rather than at access-token expiry. It plugs
 * into the decoder's validator chain, which is the supported Spring Security extension point, so no
 * custom authentication filter is involved.
 *
 * <p>Cost: one indexed lookup by {@code _id} per authenticated admin request.
 */
public final class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

	private final AdminSessionRepository adminSessionRepository;
	private final Clock clock;

	public ActiveSessionValidator(AdminSessionRepository adminSessionRepository, Clock clock) {
		this.adminSessionRepository = adminSessionRepository;
		this.clock = clock;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		String sessionId = token.getClaimAsString(TokenClaims.SESSION_ID);

		// A token without a session claim can never be tied back to revocable state, so it is
		// rejected rather than trusted.
		boolean active = sessionId != null && !sessionId.isBlank() && this.adminSessionRepository
				.existsByIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, this.clock.instant());

		if (active) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(
				new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The session is no longer active", null));
	}

}
