package com.tf.reader.auth.oidc.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Exchanges the reader's own username and password for tokens, <b>server to server</b>, using
 * the OAuth 2.0 {@code password} grant (RFC 6749 §4.3).
 *
 * <p>The credential travels once, from our backend to the provider, over a connection the
 * reader's client never sees. It is never logged and never stored - only forwarded, in this one
 * request, and then forgotten.
 *
 * <p>The client secret proves we are the application the request is issued to. It is sent in the
 * request body ({@code client_secret_post}), which both Azure AD B2C and the local mock accept.
 *
 * <p><b>Nothing here is provider-specific.</b> The url comes from configuration; the request is
 * the one RFC 6749 §4.3.2 specifies. Pointing {@code tnf.auth.oidc.token-uri} at B2C instead of
 * the mock is the entire migration, as far as this class is concerned.
 */
@Component
public class OidcTokenClient {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(OidcTokenClient.class);

	/**
	 * Timeouts, because this call sits in the middle of a user's sign-in.
	 *
	 * <p>Without them a provider that accepts a connection and then goes quiet holds a request
	 * thread until the container gives up, and enough simultaneous sign-ins against a sick
	 * provider take the whole application down with it. Five seconds is generous for a token
	 * exchange and short enough that a user sees a refusal rather than a hang.
	 */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient http;
	private final OidcProperties properties;

	public OidcTokenClient(OidcProperties properties) {
		this.properties = properties;

		// Built here rather than injected: there is no RestClient.Builder bean in this application,
		// and a shared one would be a shared configuration for a call whose timeouts are its own
		// concern.
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);

		this.http = RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	/**
	 * @param username the reader's own username, presented directly to the provider
	 * @param password the reader's own password, presented directly to the provider
	 * @return the token response, whose ID token is <b>not yet validated</b> - it is a string
	 *         from the network until {@link com.tf.reader.auth.oidc.validation.OidcIdTokenValidator} has finished with it
	 * @throws ApiException 401 if the exchange fails for any reason, including a wrong password
	 */
	public OidcTokenResponse exchangePassword(String username, String password) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "password");
		form.add("username", username);
		form.add("password", password);
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("scope", properties.scopeParameter());

		try {
			// Deliberately NOT logged with the username or password in it - both are credentials,
			// and logs outlive a request.
			log.debug("OIDC token exchange: POST {}", properties.tokenUri());

			@SuppressWarnings("unchecked")
			Map<String, Object> body = http.post()
					.uri(properties.tokenUri())
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.accept(MediaType.APPLICATION_JSON)
					.body(form)
					.retrieve()
					.body(Map.class);

			if (body == null) {
				throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
						"The identity provider returned an empty token response.");
			}

			OidcTokenResponse tokens = OidcTokenResponse.from(body);
			log.debug("OIDC token exchange succeeded: {}", tokens);
			return tokens;
		}
		catch (RestClientException failure) {
			// The upstream body is never copied into the response and never logged in full: a
			// provider's error payload carries correlation ids and configuration detail, and a
			// failed exchange can quote the request - which contains the client secret.
			log.warn("OIDC token exchange failed: {}", failure.getClass().getSimpleName());
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The authorization code could not be exchanged.");
		}
	}
}
