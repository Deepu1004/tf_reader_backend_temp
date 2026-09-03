package com.tf.reader.auth.saml.mock.controller;

import java.net.URI;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.tf.reader.auth.saml.mock.service.SamlMockResponse;
import com.tf.reader.auth.saml.mock.service.SamlMockResponseBuilder;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The local mock SAML IdP's one endpoint - the local equivalent of {@code https://samlmock.dev/idp}.
 *
 * <p><b>Answers the ACS itself, server-side, rather than handing a browser a form to submit.</b>
 * A real IdP is a different origin and has no choice but the classic HTTP-POST binding trick - an
 * auto-submitting HTML form, because only a browser executing it can turn "arrived via redirect"
 * into "posted from this origin". This mock is <em>in the same process</em> as the relying party
 * it answers, so it can make that POST itself instead - which is what makes the whole round trip
 * driveable from Postman, with no browser involved at any point.
 *
 * <p><b>The caller's session cookie has to come along.</b> The ACS checks {@code InResponseTo}
 * against the AuthnRequest Spring Security stashed in a session when {@code /saml2/authenticate}
 * ran. Our own call to the ACS is a new request unless it carries that same cookie, so it is read
 * off the incoming request and forwarded explicitly - this is the one thing this class does that
 * a real IdP, on a different origin, structurally could not.
 *
 * <p>No login page and no consent step: unlike the OIDC mock, there is only ever one identity to
 * authenticate as, configured in {@code saml-mock.user} - a page asking "sign in as the
 * pre-populated user?" would be theatre with no decision behind it.
 *
 * <p><b>Never enabled by default</b>, for the same reason the OIDC mock is not: a mock identity
 * provider is a machine for minting identities for arbitrary users.
 */
@RestController
@ConditionalOnProperty(prefix = "saml-mock", name = "enabled", havingValue = "true")
public class SamlMockController {

	/** Must equal {@code assertingparty.entity-id} for the {@code tf-reader} registration. */
	public static final String ENTITY_ID = "saml-mock-local";

	public static final String SSO_PATH = "/saml-mock/sso";

	private final SamlMockResponseBuilder responses;
	private final RestClient restClient;

	public SamlMockController(SamlMockResponseBuilder responses) {
		this.responses = responses;
		// Redirects disabled: the ACS itself now answers with a 302 to tfreader://auth/callback
		// on both success and failure. A client that followed that redirect would try to route
		// an unroutable custom scheme and blow up with a ClientProtocolException; this class's
		// whole job is to hand that response back untouched, not to chase it.
		this.restClient = RestClient.builder()
				.requestFactory(new HttpComponentsClientHttpRequestFactory(
						HttpClients.custom().disableRedirectHandling().build()))
				.build();
	}

	/**
	 * Redirect-binding SSO: decode the AuthnRequest, sign a Response answering it, then post the
	 * result to the ACS ourselves and hand back whatever the ACS answered - a
	 * {@code tfreader://auth/callback} redirect, on either success or failure, now that the ACS
	 * itself no longer returns a JSON body.
	 *
	 * <p><b>Every header the ACS set is forwarded, not just content type.</b> The redirect this
	 * hands back to Postman lives entirely in {@code Location}; copying only content type would
	 * answer 302 with no way to see where to.
	 * result to the ACS ourselves and hand back whatever the ACS answered - a
	 * {@code tfreader://auth/callback} redirect, on either success or failure, now that the ACS
	 * itself no longer returns a JSON body.
	 *
	 * <p><b>Every header the ACS set is forwarded, not just content type.</b> The redirect this
	 * hands back to Postman lives entirely in {@code Location}; copying only content type would
	 * answer 302 with no way to see where to.
	 * result to the ACS ourselves and hand back whatever the ACS answered - a
	 * {@code tfreader://auth/callback} redirect, on either success or failure, now that the ACS
	 * itself no longer returns a JSON body.
	 *
	 * <p><b>Every header the ACS set is forwarded, not just content type.</b> The redirect this
	 * hands back to Postman lives entirely in {@code Location}; copying only content type would
	 * answer 302 with no way to see where to.
	 */
	@GetMapping(SSO_PATH)
	public ResponseEntity<byte[]> sso(
			@RequestParam("SAMLRequest") String samlRequest,
			@RequestParam(name = "RelayState", required = false) String relayState,
			HttpServletRequest request) throws java.io.IOException {

		SamlMockResponse response = responses.build(samlRequest);

		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("SAMLResponse", response.value());
		if (relayState != null) {
			body.add("RelayState", relayState);
		}

		return restClient.post()
				.uri(URI.create(response.acsUrl()))
				.header(HttpHeaders.COOKIE, request.getHeader(HttpHeaders.COOKIE))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(body)
				.exchange((acsRequest, acsResponse) -> {
					HttpHeaders forwarded = new HttpHeaders();
					forwarded.addAll(acsResponse.getHeaders());
					// Recomputed from the body actually sent below, not copied: forwarding the
					// ACS's own framing headers would fight with whatever length this response's
					// byte[] body ends up being written with.
					forwarded.remove(HttpHeaders.CONTENT_LENGTH);
					forwarded.remove(HttpHeaders.TRANSFER_ENCODING);

					return ResponseEntity.status(acsResponse.getStatusCode())
							.headers(forwarded)
							.body(acsResponse.getBody().readAllBytes());
				});
	}
}
