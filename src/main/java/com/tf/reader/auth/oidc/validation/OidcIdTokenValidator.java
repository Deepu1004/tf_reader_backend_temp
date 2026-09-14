package com.tf.reader.auth.oidc.validation;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Validates an ID token before its claims are read.
 *
 * <p>{@link OidcIdTokenDecoder} answers "is this a genuine, current token from our provider, for
 * us?" - signature against the JWKS, issuer, audience, expiry. Those checks are the same for
 * every sign-in, so they are configuration, and they live in {@code auth.security} where the
 * architecture rules put token decoding. This class is the thin OIDC-flow-level wrapper around
 * it.
 *
 * <p><b>There is no nonce check here.</b> A nonce defends a browser redirect - it binds the
 * token to the specific authorization request that started it, because the token arrives back
 * through a third party the backend does not otherwise control. Sign-in is now the password
 * grant: the reader's credentials go to the provider and the token comes straight back on the
 * same connection, in the same request, with no redirect in between for anything to be replayed
 * into.
 */
@Component
public class OidcIdTokenValidator {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(OidcIdTokenValidator.class);

	private final OidcIdTokenDecoder decoder;

	public OidcIdTokenValidator(OidcIdTokenDecoder decoder) {
		this.decoder = decoder;
	}

	/**
	 * @param idToken the raw ID token from the token endpoint
	 * @return the verified token, whose claims are now safe to read
	 * @throws ApiException 401 if the token is missing or fails signature/issuer/audience/expiry
	 */
	public Jwt validate(String idToken) {
		if (!StringUtils.hasText(idToken)) {
			// A token response with no id_token is a provider configured without the openid scope.
			// It must refuse rather than sign somebody in on an access token, which is an
			// authorization grant and not an assertion about who anybody is.
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The identity provider returned no ID token.");
		}

		Jwt verified = this.decoder.verify(idToken);
		log.debug("OIDC ID token accepted");
		return verified;
	}
}
