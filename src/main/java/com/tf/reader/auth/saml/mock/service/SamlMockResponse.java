package com.tf.reader.auth.saml.mock.service;

/**
 * What the mock IdP produced for one AuthnRequest: where it goes, and what it is.
 *
 * @param acsUrl the AssertionConsumerServiceURL read off the AuthnRequest - where the signed
 *               response has to be delivered
 * @param value  the base64-encoded, signed {@code SAMLResponse}
 */
public record SamlMockResponse(String acsUrl, String value) {
}
