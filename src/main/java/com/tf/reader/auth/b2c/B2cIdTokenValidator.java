package com.tf.reader.auth.b2c;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tf.reader.auth.oidc.validation.B2cIdTokenDecoder;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Validates a B2C ID token <b>against the sign-in it is supposed to belong to</b>.
 *
 * <p>The individual-flow counterpart of
 * {@link com.tf.reader.auth.oidc.validation.OidcIdTokenValidator} - identical nonce check, against
 * a {@link B2cTransaction} instead of an {@code OidcTransaction}. See that class's javadoc for why
 * the nonce matters even once the state has already matched.
 */
@Component
public class B2cIdTokenValidator {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(B2cIdTokenValidator.class);

	private final B2cIdTokenDecoder decoder;

	public B2cIdTokenValidator(B2cIdTokenDecoder decoder) {
		this.decoder = decoder;
	}

	/**
	 * @param idToken     the raw ID token from the token endpoint
	 * @param transaction the sign-in it must belong to
	 * @return the verified token, whose claims are now safe to read
	 * @throws ApiException 401 if any check fails
	 */
	public Jwt validate(String idToken, B2cTransaction transaction) {
		if (!StringUtils.hasText(idToken)) {
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The identity provider returned no ID token.");
		}

		Jwt verified = this.decoder.verify(idToken);
		requireMatchingNonce(verified, transaction);

		log.debug("B2C ID token accepted for transaction {}", transaction.id());
		return verified;
	}

	private static void requireMatchingNonce(Jwt idToken, B2cTransaction transaction) {
		String presented = idToken.getClaimAsString("nonce");

		if (!StringUtils.hasText(presented) || !constantTimeEquals(presented, transaction.nonce())) {
			log.warn("B2C nonce mismatch for transaction {} - the token was not minted for this "
					+ "authorization request", transaction.id());
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The identity provider's token could not be validated.");
		}
	}

	private static boolean constantTimeEquals(String presented, String expected) {
		return MessageDigest.isEqual(
				presented.getBytes(StandardCharsets.UTF_8),
				expected.getBytes(StandardCharsets.UTF_8));
	}
}
