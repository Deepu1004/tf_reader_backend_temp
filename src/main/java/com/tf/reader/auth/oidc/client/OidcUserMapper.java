package com.tf.reader.auth.oidc.client;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.repository.ReaderUserDirectory;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Turns a validated OIDC ID token into a TnF user.
 *
 * <p>The individual counterpart of {@link com.tf.reader.auth.saml.SamlUserMapper} - same
 * claim-reading rules, but there is no institution to resolve a membership against. An identity
 * here <b>is</b> the account: the first successful sign-in for an email provisions it
 * ({@link ReaderUserDirectory#findOrProvisionIndividual}), rather than being refused as
 * unprovisioned the way an institutional sign-in is.
 *
 * <p>Takes a {@link Jwt} because that is what verification produces - a token whose signature,
 * issuer, audience, expiry and nonce have all been checked. There is no overload taking a raw
 * string, so there is no path by which an unverified token reaches a user lookup.
 *
 * <p><b>What is deliberately NOT read from any claim:</b> roles, collections and user type. Those
 * are this application's authorization model and they come from our own user store. A claim is
 * the provider's statement about identity, not a grant of authority here - and a {@code roles}
 * claim honoured at this line would let anyone who can edit the identity provider's user flow
 * output claims, or anyone who can reconfigure the mock, make themselves an administrator of the
 * Reader.
 */
@Component
@EnableConfigurationProperties(OidcProperties.class)
public class OidcUserMapper {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(OidcUserMapper.class);

	private final ReaderUserDirectory users;
	private final OidcProperties.Claims claims;

	public OidcUserMapper(ReaderUserDirectory users, OidcProperties properties) {
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

		// The user id, not the email: an address is personal data and this line ends up in a log
		// file that outlives the request.
		log.info("OIDC user resolved: {}", user.userId());
		return user;
	}

	/**
	 * The first configured email claim carrying usable text.
	 *
	 * @throws ApiException 401 if none of them do. Refused rather than defaulted: an identity we
	 *                      cannot name is not one we can look a membership up for, and guessing
	 *                      (the subject, say) would look a user up by a value that is not an email
	 *                      address at all
	 */
	String resolveEmail(Jwt idToken) {
		String email = firstUsableClaim(idToken.getClaims(), claims.email());
		if (email == null) {
			// Names which claims were looked for, not what the token contained: the claim set of a
			// real user is not something to write into a response or a log.
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The ID token carried no email address in any of the claims we read.");
		}
		return email;
	}

	/**
	 * The provider's stable identifier for this user, for the audit trail. Optional: it is
	 * evidence, not identity, so a token without one still signs in.
	 */
	String resolveSubject(Jwt idToken) {
		return firstUsableClaim(idToken.getClaims(), claims.subject());
	}

	/**
	 * Reads a claim that may be a string or a list of them.
	 *
	 * <p>Azure AD B2C emits {@code emails} as a JSON <b>array</b> even when it holds one address,
	 * while {@code email} and {@code preferred_username} - and our mock - are plain strings.
	 * Handling both here is what lets one configuration serve a B2C user flow, the Microsoft
	 * identity platform and the local mock. Anything that is neither is skipped rather than
	 * coerced: {@code String.valueOf} on a map would produce a plausible-looking lookup key out
	 * of nothing.
	 */
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
