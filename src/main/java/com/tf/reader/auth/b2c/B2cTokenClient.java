package com.tf.reader.auth.b2c;

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

import com.tf.reader.auth.oidc.client.OidcTokenResponse;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Exchanges a B2C authorization code for tokens, server to server.
 *
 * <p>The individual-flow counterpart of
 * {@link com.tf.reader.auth.oidc.client.OidcTokenClient} - identical mechanics, RFC 6749 §4.1.3,
 * against {@link B2cProperties} instead. Returns {@link OidcTokenResponse} rather than a second,
 * identical DTO: parsing a token response out of a map has no institution or B2C-specific shape
 * to it at all.
 */
@Component
public class B2cTokenClient {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(B2cTokenClient.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

	private final RestClient http;
	private final B2cProperties properties;

	public B2cTokenClient(B2cProperties properties) {
		this.properties = properties;

		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);

		this.http = RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	/**
	 * @param code the authorization code the provider sent to our callback
	 * @return the token response, whose ID token is <b>not yet validated</b>
	 * @throws ApiException 401 if the exchange fails for any reason
	 */
	public OidcTokenResponse exchangeAuthorizationCode(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("code", code);
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		form.add("redirect_uri", properties.redirectUri());

		try {
			// Deliberately NOT logged with the code in it. An authorization code is a credential:
			// short-lived and single use, but a credential, and logs outlive both.
			log.debug("B2C token exchange: POST {}", properties.tokenUri());

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
			log.debug("B2C token exchange succeeded: {}", tokens);
			return tokens;
		}
		catch (RestClientException failure) {
			// The upstream body is never copied into the response and never logged in full: a
			// provider's error payload carries correlation ids and configuration detail, and a
			// failed exchange can quote the request - which contains the client secret.
			log.warn("B2C token exchange failed: {}", failure.getClass().getSimpleName());
			throw new ApiException(ErrorCode.OIDC_AUTHENTICATION_FAILED,
					"The authorization code could not be exchanged.");
		}
	}
}
