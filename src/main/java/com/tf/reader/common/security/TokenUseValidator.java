package com.tf.reader.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires the {@code token_use} claim to match the expected value.
 *
 * <p>Second, independent barrier alongside {@link ExactAudienceValidator}: an access token and a
 * refresh token are separated both by audience and by intent, so neither check alone is load
 * bearing.
 */
public final class TokenUseValidator implements OAuth2TokenValidator<Jwt> {

	private final String expectedTokenUse;

	public TokenUseValidator(String expectedTokenUse) {
		this.expectedTokenUse = expectedTokenUse;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (this.expectedTokenUse.equals(token.getClaimAsString(TokenClaims.TOKEN_USE))) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
				"The token is not a " + this.expectedTokenUse + " token", null));
	}

}
