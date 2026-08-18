package com.tf.reader.auth.saml;

import java.util.List;

import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.repository.MockUserRepository;
import com.tf.reader.shared.error.ApiException;
import com.tf.reader.shared.error.ErrorCode;

/**
 * Turns a validated SAML assertion into a TnF user.
 *
 * <p>The IdP tells us who someone is. The institution comes from the sign-in transaction our
 * own backend opened, never from the assertion and never from the client. This class is where
 * those two facts meet, and it is the only place they do.
 *
 * <p>Reads {@link Saml2ResponseAssertionAccessor} rather than the deprecated
 * {@code Saml2AuthenticatedPrincipal}, which Spring Security 7 replaced it with.
 */
@Component
public class SamlUserMapper {

	/**
	 * The claim samlmock.dev uses for the email address. It is the WS-Federation style URI most
	 * IdPs emit, and it is what appears in the mock's default assertion.
	 */
	static final String EMAIL_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress";

	private final MockUserRepository users;

	public SamlUserMapper(MockUserRepository users) {
		this.users = users;
	}

	/**
	 * @param assertion     an assertion Spring Security has already validated
	 * @param institutionId the institution recovered from the sign-in transaction
	 * @throws ApiException 403 if the identity holds no membership at that institution
	 */
	public TnfUser map(Saml2ResponseAssertionAccessor assertion, String institutionId) {
		String email = resolveEmail(assertion);
		return users.find(email, institutionId)
				.orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_PROVISIONED,
						"This identity holds no membership at institution '" + institutionId + "'."));
	}

	/**
	 * Prefers the email attribute and falls back to the NameID, because an IdP may assert the
	 * subject in either place and samlmock.dev happens to put the same value in both.
	 */
	String resolveEmail(Saml2ResponseAssertionAccessor assertion) {
		List<Object> emails = assertion.getAttributes().get(EMAIL_CLAIM);
		if (emails != null) {
			for (Object candidate : emails) {
				if (candidate instanceof String value && StringUtils.hasText(value)) {
					return value;
				}
			}
		}
		String nameId = assertion.getNameId();
		if (!StringUtils.hasText(nameId)) {
			throw new ApiException(ErrorCode.SAML_AUTHENTICATION_FAILED,
					"The SAML assertion carried no subject we could identify.");
		}
		return nameId;
	}
}
