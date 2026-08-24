package com.tf.reader.auth.saml.mock.model;

/**
 * The single identity the local mock SAML IdP asserts.
 *
 * <p>Defaults to {@code john.doe@example.com} - the same NameID the real samlmock.dev flow puts
 * in its default assertion - so {@code MockUserRepository} needs no second fixture and a sign-in
 * looks the same regardless of which IdP answered it.
 *
 * @param nameId the value the mock puts in the assertion's {@code NameID}, and the address
 *               {@code SamlUserMapper} falls back to when there is no email attribute
 */
public record SamlMockUser(String nameId) {

	public SamlMockUser {
		nameId = (nameId != null) ? nameId : "john.doe@example.com";
	}

	public static SamlMockUser defaultUser() {
		return new SamlMockUser(null);
	}
}
