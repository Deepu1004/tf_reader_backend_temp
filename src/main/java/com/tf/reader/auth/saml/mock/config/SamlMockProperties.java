package com.tf.reader.auth.saml.mock.config;

import com.tf.reader.auth.saml.mock.model.SamlMockUser;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The local mock IdP's own configuration, bound from {@code saml-mock.*}.
 *
 * <p>Separate from {@code spring.security.saml2.relyingparty.*} for the same reason the OIDC
 * mock keeps {@code mock-oidc.*} apart from {@code tnf.auth.oidc.*}: those are the relying
 * party's settings; these are the (mock) asserting party's. Deleting this whole package changes
 * nothing about the relying party - only which url and certificate it is pointed at.
 *
 * <p><b>{@link #enabled} defaults to false.</b> A mock identity provider that could be switched
 * on by forgetting to switch it off is a way to mint tokens for arbitrary users.
 *
 * @param enabled    whether the mock exists at all. Local development and tests only
 * @param privateKey the PEM-encoded PKCS8 RSA private key the mock signs assertions with. Read
 *                   from {@code application-local.yml}, which is gitignored - not a credential to
 *                   anything real, but not written to source control either. Its matching public
 *                   certificate is the one committed as {@code saml-mock-idp-local.crt}
 * @param user       the single identity the mock asserts
 */
@ConfigurationProperties(prefix = "saml-mock")
public record SamlMockProperties(boolean enabled, String privateKey, SamlMockUser user) {

	public SamlMockProperties {
		user = (user != null) ? user : SamlMockUser.defaultUser();
	}
}
