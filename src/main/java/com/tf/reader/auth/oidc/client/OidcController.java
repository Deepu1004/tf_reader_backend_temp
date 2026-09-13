package com.tf.reader.auth.oidc.client;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.dto.OidcPasswordLoginRequest;
import com.tf.reader.auth.dto.OidcTokenRequest;
import com.tf.reader.auth.dto.TokenResponse;
import com.tf.reader.auth.token.AuthorizationCodeStore;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * The two OIDC endpoints: authenticating the individual, no-institution reader against the
 * provider, and redeeming the one-time id that call returns for a real token pair.
 *
 * <p>HTTP only. Every decision lives in {@link OidcAuthenticationService}, which is why that
 * class can be tested without a servlet and this one has almost nothing in it. The same split
 * the SAML leg uses, where the handlers do HTTP and the service does the deciding.
 *
 * <p><b>Both routes are public, and both have to be.</b> {@code /start} is how a caller obtains
 * a credential, so it cannot require one; {@code /token} carries only a one-time id that is
 * meaningless to anybody who is not the caller who just received it.
 *
 * <p><b>No browser, no redirect, no session.</b> Unlike the SAML leg, this flow never leaves our
 * own API: the reader's client collects the username and password itself and posts them here
 * directly. Both endpoints answer with a plain JSON body, never a redirect, and neither creates
 * an {@code HttpSession} - the whole flow runs on the stateless API chain, exactly like every
 * other {@code /api/**} route.
 */
@RestController
@RequestMapping("/api/v1/auth/oidc")
public class OidcController {

	private final OidcAuthenticationService authentication;
	private final AuthorizationCodeStore authorizationCodes;

	public OidcController(OidcAuthenticationService authentication,
			AuthorizationCodeStore authorizationCodes) {
		this.authentication = authentication;
		this.authorizationCodes = authorizationCodes;
	}

	/**
	 * Authenticates a reader's own username and password against the provider.
	 *
	 * <p>On success, a token pair has already been minted and is waiting behind the returned
	 * {@code oidcTxnId} - redeem it at {@link #token} to actually receive it. A bad username or
	 * password is refused here, by the provider, as {@code OIDC_AUTHENTICATION_FAILED}.
	 */
	@PostMapping("/start")
	public OidcStartResponse start(@Valid @RequestBody OidcPasswordLoginRequest request) {
		return authentication.startPasswordLogin(request.username(), request.password());
	}

	/**
	 * Redeems the one-time id from {@link #start} for the access and refresh token pair it was
	 * minted for. The pair was minted once, at {@code /start}, alongside the id that stands for
	 * it - this call never mints anything new.
	 */
	@PostMapping("/token")
	public TokenResponse token(@Valid @RequestBody OidcTokenRequest request) {
		return authorizationCodes.consume(request.oidcTxnId())
				.orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID,
						"This id is unknown, already used, or expired."));
	}
}
