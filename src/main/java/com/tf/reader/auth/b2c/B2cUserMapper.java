package com.tf.reader.auth.b2c;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.oidc.client.OidcProperties;
import com.tf.reader.auth.repository.ReaderUserDirectory;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Turns a validated B2C ID token into a TnF user.
 *
 * <p>The individual counterpart of
 * {@link com.tf.reader.auth.oidc.client.OidcUserMapper} - same claim-reading rules, since a B2C
 * ID token has the same shape regardless of which user flow issued it - but there is no
 * institution to resolve a membership against. An identity here <b>is</b> the account: the first
 * successful sign-in for an email provisions it ({@link ReaderUserDirectory#findOrProvisionIndividual}),
 * rather than being refused as unprovisioned the way an institution sign-in is.
 *
 * <p>What is deliberately NOT read from any claim: roles, collections, user type. Exactly the same
 * reasoning as the OIDC mapper - a claim is the provider's statement about identity, not a grant
 * of authority here.
 */
@Component
@EnableConfigurationProperties(B2cProperties.class)
public class B2cUserMapper {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(B2cUserMapper.class);

	private final ReaderUserDirectory users;
	private final OidcProperties.Claims claims;

	public B2cUserMapper(ReaderUserDirectory users, B2cProperties properties) {
		this.users = users;
		this.claims = properties.claims();
	}

	/**
	 * @param idToken an ID token that has already been fully validated
	 * @throws ApiException 401 if the token carries no email we can identify
	 */
	public TnfUser map(Jwt idToken) {
		String email = resolveEmail(idToken);

		TnfUser user = users.findOrProvisionIndividual(email);
		log.info("B2C user resolved: {}", user.userId());
		return user;
	}

	/**
	 * The first configured email claim carrying usable text.
	 *
	 * @throws ApiException 401 if none of them do
	 */
	String resolveEmail(Jwt idToken) {
		String email = firstUsableClaim(idToken.getClaims(), claims.email());
		if (email == null) {
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The ID token carried no email address in any of the claims we read.");
		}
		return email;
	}

	/** The provider's stable identifier for this user, for the audit trail. Optional. */
	String resolveSubject(Jwt idToken) {
		return firstUsableClaim(idToken.getClaims(), claims.subject());
	}

	private static String firstUsableClaim(Map<String, Object> allClaims, List<String> candidates) {
		for (String name : candidates) {
			Object value = allClaims.get(name);
			if (value instanceof String text && StringUtils.hasText(text)) {
				return text;
			}
			if (value instanceof List<?> values) {
				for (Object element : values) {
					if (element instanceof String text && StringUtils.hasText(text)) {
						return text;
					}
				}
			}
		}
		return null;
	}
}
