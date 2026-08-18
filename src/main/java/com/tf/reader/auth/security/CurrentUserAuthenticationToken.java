package com.tf.reader.auth.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.auth.model.CurrentUser;

/**
 * The {@code Authentication} for a request that presented a valid bearer token.
 *
 * <p>Exists so the principal is a {@link CurrentUser} rather than a raw {@code Jwt}. Spring's
 * own {@code JwtAuthenticationToken} makes the token itself the principal, which would put
 * claim-reading into every controller that wants a userId - the thing the mapping stage exists
 * to prevent. With this, a controller writes {@code @AuthenticationPrincipal CurrentUser} and
 * never sees a claim.
 *
 * <p>The verified {@code Jwt} is kept as the credentials, where Spring Security expects a
 * credential to live, and out of {@code CurrentUser}.
 */
public class CurrentUserAuthenticationToken extends AbstractAuthenticationToken {

	private final transient CurrentUser principal;
	private final transient Jwt credentials;

	public CurrentUserAuthenticationToken(CurrentUser principal, Jwt credentials,
			Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		this.credentials = credentials;
		// Only ever constructed from a token the decoder has verified and validated.
		setAuthenticated(true);
	}

	@Override
	public CurrentUser getPrincipal() {
		return principal;
	}

	@Override
	public Jwt getCredentials() {
		return credentials;
	}

	@Override
	public String getName() {
		return principal.userId();
	}
}
