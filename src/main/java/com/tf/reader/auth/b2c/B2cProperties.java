package com.tf.reader.auth.b2c;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.tf.reader.auth.oidc.client.OidcProperties;

/**
 * Everything the backend needs to be an OpenID Connect relying party for the individual,
 * email-and-password sign-in flow, bound from {@code tnf.auth.b2c.*}.
 *
 * <p>Field for field {@link OidcProperties}, and deliberately its own configuration block rather
 * than a shared one: this flow registers its own callback ({@code redirectUri}) at the provider,
 * and keeping the two independent is what lets one be repointed at a different app registration
 * without touching the other. See {@link OidcProperties} for what each field means - the claim
 * mapping ({@link OidcProperties.Claims}) is reused as-is, because reading an email out of an ID
 * token is exactly the same problem for either flow.
 */
@ConfigurationProperties(prefix = "tnf.auth.b2c")
public record B2cProperties(
		String clientId,
		String clientSecret,
		String issuer,
		String authorizationUri,
		String tokenUri,
		String jwkSetUri,
		String redirectUri,
		List<String> scopes,
		Duration transactionTtl,
		OidcProperties.Claims claims) {

	private static final List<String> DEFAULT_SCOPES = List.of("openid", "profile", "email");

	/** Long enough for a human to work through a sign-in page, short enough to be useless later. */
	private static final Duration DEFAULT_TRANSACTION_TTL = Duration.ofMinutes(10);

	public B2cProperties {
		scopes = (scopes == null || scopes.isEmpty()) ? DEFAULT_SCOPES : List.copyOf(scopes);
		transactionTtl = (transactionTtl != null) ? transactionTtl : DEFAULT_TRANSACTION_TTL;
		claims = (claims != null) ? claims : OidcProperties.Claims.defaults();
	}

	/** The scopes as the space-delimited string an authorization request carries. */
	public String scopeParameter() {
		return String.join(" ", scopes);
	}
}
