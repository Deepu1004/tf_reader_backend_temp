package com.tf.reader.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Requires {@code token_use} to match, so audience is not the only thing separating token types. */
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
