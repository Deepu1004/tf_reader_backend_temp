package com.tf.reader.auth.saml.mock.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.xml.namespace.QName;

import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.SAMLObjectContentReference;
import org.opensaml.saml.common.SAMLVersion;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Status;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureConstants;
import org.opensaml.xmlsec.signature.support.Signer;
import org.w3c.dom.Element;

import net.shibboleth.shared.xml.SerializeSupport;
import net.shibboleth.shared.xml.impl.BasicParserPool;

import com.tf.reader.auth.saml.mock.config.SamlMockComponent;
import com.tf.reader.auth.saml.mock.config.SamlMockProperties;
import com.tf.reader.auth.saml.mock.controller.SamlMockController;
import com.tf.reader.auth.saml.mock.security.SamlMockKeyService;

/**
 * Builds and signs the mock IdP's one product: a SAML Response asserting the configured user.
 *
 * <p>Genuinely signed with a real RSA-SHA256 signature over a real OpenSAML assertion - the same
 * library the relying party uses to validate it. A mock that produced anything less would leave
 * the signature path, the one check that matters most, untested until it met a real IdP.
 *
 * <p>{@code InResponseTo} is copied from the AuthnRequest this response answers, which is why the
 * request has to be decoded rather than ignored: Spring Security's entry point stashes the
 * outbound AuthnRequest in the caller's session, and the ACS refuses a response that does not
 * name it.
 */
@SamlMockComponent
public class SamlMockResponseBuilder {

	private static final String AUTHN_CONTEXT_PASSWORD =
			"urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport";

	/** How long the assertion is valid for, both directions from "now". Long enough for a
	 * developer to read the response, short enough to look like a real IdP's window. */
	private static final Duration ASSERTION_VALIDITY = Duration.ofMinutes(5);

	static {
		try {
			// Idempotent, and defensive: Spring Security's own SAML support already does this
			// somewhere in the same JVM, but this class must not depend on running after it.
			InitializationService.initialize();
		}
		catch (InitializationException failure) {
			throw new IllegalStateException("OpenSAML failed to initialise", failure);
		}
	}

	private final SamlMockProperties properties;
	private final SamlMockKeyService keys;
	private final Clock clock;
	private final BasicParserPool parserPool;

	public SamlMockResponseBuilder(SamlMockProperties properties, SamlMockKeyService keys, Clock clock) {
		this.properties = properties;
		this.keys = keys;
		this.clock = clock;
		this.parserPool = newParserPool();
	}

	/**
	 * @param samlRequest the redirect-binding {@code SAMLRequest} parameter: base64 of a raw
	 *                    (unwrapped) DEFLATE stream, exactly as
	 *                    {@code OpenSaml5AuthenticationRequestResolver} produced it
	 */
	public SamlMockResponse build(String samlRequest) {
		AuthnRequest authnRequest = decodeAuthnRequest(samlRequest);
		Response response = buildResponse(authnRequest);
		return new SamlMockResponse(authnRequest.getAssertionConsumerServiceURL(), encode(response));
	}

	private AuthnRequest decodeAuthnRequest(String samlRequest) {
		byte[] deflated = Base64.getMimeDecoder().decode(samlRequest);
		try (InputStream inflated =
				new InflaterInputStream(new ByteArrayInputStream(deflated), new Inflater(true))) {
			XMLObject xmlObject = XMLObjectSupport.unmarshallFromInputStream(parserPool, inflated);
			if (!(xmlObject instanceof AuthnRequest authnRequest)) {
				throw new IllegalArgumentException("SAMLRequest was not an AuthnRequest");
			}
			return authnRequest;
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("SAMLRequest could not be decoded", failure);
		}
	}

	private Response buildResponse(AuthnRequest authnRequest) {
		Instant now = clock.instant();
		String acsUrl = authnRequest.getAssertionConsumerServiceURL();
		String spEntityId = authnRequest.getIssuer().getValue();

		Assertion assertion = build(Assertion.DEFAULT_ELEMENT_NAME);
		assertion.setVersion(SAMLVersion.VERSION_20);
		assertion.setID(newId());
		assertion.setIssueInstant(now);
		assertion.setIssuer(issuer());
		assertion.setSubject(subject(authnRequest.getID(), acsUrl, now));
		assertion.setConditions(conditions(spEntityId, now));
		assertion.getAuthnStatements().add(authnStatement(now));
		sign(assertion);

		Response response = build(Response.DEFAULT_ELEMENT_NAME);
		response.setVersion(SAMLVersion.VERSION_20);
		response.setID(newId());
		response.setInResponseTo(authnRequest.getID());
		response.setIssueInstant(now);
		response.setDestination(acsUrl);
		response.setIssuer(issuer());
		response.setStatus(success());
		response.getAssertions().add(assertion);
		return response;
	}

	private Subject subject(String inResponseTo, String recipient, Instant now) {
		NameID nameId = build(NameID.DEFAULT_ELEMENT_NAME);
		nameId.setValue(properties.user().nameId());
		nameId.setFormat(NameID.EMAIL);

		SubjectConfirmationData confirmationData = build(SubjectConfirmationData.DEFAULT_ELEMENT_NAME);
		confirmationData.setInResponseTo(inResponseTo);
		confirmationData.setRecipient(recipient);
		confirmationData.setNotOnOrAfter(now.plus(ASSERTION_VALIDITY));

		SubjectConfirmation confirmation = build(SubjectConfirmation.DEFAULT_ELEMENT_NAME);
		confirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
		confirmation.setSubjectConfirmationData(confirmationData);

		Subject subject = build(Subject.DEFAULT_ELEMENT_NAME);
		subject.setNameID(nameId);
		subject.getSubjectConfirmations().add(confirmation);
		return subject;
	}

	private Conditions conditions(String audience, Instant now) {
		Audience aud = build(Audience.DEFAULT_ELEMENT_NAME);
		aud.setURI(audience);

		AudienceRestriction restriction = build(AudienceRestriction.DEFAULT_ELEMENT_NAME);
		restriction.getAudiences().add(aud);

		Conditions conditions = build(Conditions.DEFAULT_ELEMENT_NAME);
		conditions.setNotBefore(now.minus(ASSERTION_VALIDITY));
		conditions.setNotOnOrAfter(now.plus(ASSERTION_VALIDITY));
		conditions.getAudienceRestrictions().add(restriction);
		return conditions;
	}

	private AuthnStatement authnStatement(Instant now) {
		AuthnContextClassRef classRef = build(AuthnContextClassRef.DEFAULT_ELEMENT_NAME);
		classRef.setURI(AUTHN_CONTEXT_PASSWORD);

		AuthnContext authnContext = build(AuthnContext.DEFAULT_ELEMENT_NAME);
		authnContext.setAuthnContextClassRef(classRef);

		AuthnStatement statement = build(AuthnStatement.DEFAULT_ELEMENT_NAME);
		statement.setAuthnInstant(now);
		statement.setAuthnContext(authnContext);
		return statement;
	}

	private Issuer issuer() {
		Issuer issuer = build(Issuer.DEFAULT_ELEMENT_NAME);
		issuer.setValue(SamlMockController.ENTITY_ID);
		return issuer;
	}

	private Status success() {
		StatusCode code = build(StatusCode.DEFAULT_ELEMENT_NAME);
		code.setValue(StatusCode.SUCCESS);

		Status status = build(Status.DEFAULT_ELEMENT_NAME);
		status.setStatusCode(code);
		return status;
	}

	private void sign(Assertion assertion) {
		Signature signature = build(Signature.DEFAULT_ELEMENT_NAME);
		signature.setSigningCredential(keys.credential());
		signature.setSignatureAlgorithm(SignatureConstants.ALGO_ID_SIGNATURE_RSA_SHA256);
		signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
		signature.getContentReferences().add(new SAMLObjectContentReference(assertion));
		assertion.setSignature(signature);
		try {
			// The object has to already be in a DOM before Signer can compute a digest over it.
			XMLObjectSupport.marshall(assertion);
			Signer.signObject(signature);
		}
		catch (Exception failure) {
			throw new IllegalStateException("the mock IdP could not sign an assertion", failure);
		}
	}

	private static String encode(Response response) {
		try {
			Element element = XMLObjectSupport.marshall(response);
			String xml = SerializeSupport.nodeToString(element);
			return Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception failure) {
			throw new IllegalStateException("the mock IdP could not serialise its response", failure);
		}
	}

	@SuppressWarnings("unchecked")
	private static <T extends XMLObject> T build(QName elementName) {
		return (T) XMLObjectSupport.buildXMLObject(elementName);
	}

	private static String newId() {
		return "_" + UUID.randomUUID();
	}

	private static BasicParserPool newParserPool() {
		BasicParserPool pool = new BasicParserPool();
		try {
			pool.initialize();
		}
		catch (Exception failure) {
			throw new IllegalStateException("could not initialise the mock IdP's XML parser", failure);
		}
		return pool;
	}
}
