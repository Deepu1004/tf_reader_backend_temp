package com.tf.reader.common.security;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Requires {@code aud} to be exactly one value, equal to the expected audience. Stricter than a
 * "contains" check on purpose: a multi-audience token must not be usable against any of them.
 */
public final class ExactAudienceValidator implements OAuth2TokenValidator<Jwt> {

	private final String expectedAudience;

	public ExactAudienceValidator(String expectedAudience) {
		this.expectedAudience = expectedAudience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		List<String> audience = token.getAudience();
		if (audience != null && audience.size() == 1 && expectedAudience.equals(audience.get(0))) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN,
				"The required audience " + this.expectedAudience + " is missing", null));
	}

}
