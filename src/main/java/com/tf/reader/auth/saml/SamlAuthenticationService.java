package com.tf.reader.auth.saml;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.stereotype.Service;

import com.tf.reader.auth.model.Institution;
import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.token.IssuedToken;
import com.tf.reader.auth.token.TokenService;
import com.tf.reader.auth.transaction.AuthTransaction;
import com.tf.reader.auth.transaction.AuthTransactionStore;
import com.tf.reader.catalogue.api.InstitutionLookup;
import com.tf.reader.catalogue.api.InstitutionRef;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Joins the two halves of a completed SAML sign-in: proof that a real assertion validated, and
 * the institution our own backend chose before the redirect.
 *
 * <p>Spring Security has already answered "was this external identity authenticated?". This
 * class does not ask who it was - see {@link SamlUserMapper} - only which sign-in it was and
 * which institution it belongs to. It knows nothing about HTTP, which is what keeps it
 * unit-testable without a servlet.
 *
 * <p><b>Where this stops.</b> It returns a {@link TnfUser} and mints nothing. TokenService, the
 * JWT and the session are the next stage of work, and this is the seam they will attach to.
 */
@Service
public class SamlAuthenticationService {

	private final AuthTransactionStore transactions;
	private final InstitutionLookup institutions;
	private final SamlUserMapper userMapper;
	private final TokenService tokenService;
	private final Clock clock;

	public SamlAuthenticationService(AuthTransactionStore transactions,
			InstitutionLookup institutions, SamlUserMapper userMapper,
			TokenService tokenService, Clock clock) {
		this.transactions = transactions;
		this.institutions = institutions;
		this.userMapper = userMapper;
		this.tokenService = tokenService;
		this.clock = clock;
	}

	/**
	 * Completes a sign-in that Spring Security has already validated.
	 *
	 * @param authentication the validated SAML authentication
	 * @param relayState     the transaction id the IdP echoed back, and the ONLY thing that
	 *                       decides the institution - a client-supplied institutionId is never
	 *                       consulted here or anywhere downstream
	 * @throws ApiException 401 if the authentication carries no assertion, or the transaction is
	 *                      unknown, already used or expired; 403 ({@code SEAT_LIMIT_REACHED}) if
	 *                      this is a new device and the institution has no concurrent seat free
	 */
	public SamlLoginResult complete(Authentication authentication, String relayState) {
		requireSamlAssertion(authentication);
		AuthTransaction transaction = transactions.consume(relayState)
				.orElseThrow(() -> new ApiException(ErrorCode.SAML_AUTHENTICATION_FAILED,
						"This sign-in could not be matched to a transaction we started."));
		Institution institution = institutionOf(transaction.institutionId());
		TnfUser user = userMapper.map(institution.institutionId(), transaction.deviceId());

		// The token is minted from the mapped user and nothing else. Note the order: a device
		// refused a seat never reaches this line, so a refusal cannot produce a token.
		IssuedToken token = tokenService.issue(user);

		return new SamlLoginResult(token.token(), token.expiresAt(),
				clock.instant().truncatedTo(ChronoUnit.SECONDS), institution, user);
	}

	/**
	 * The institution is recovered from a transaction we opened, never from the assertion and
	 * never from the request. One IdP serves every institution, so the assertion cannot tell us
	 * which one this is - and if the client could, it could pick any of them.
	 */
	private Institution institutionOf(String institutionId) {
		InstitutionRef institutionRef = institutions.find(institutionId)
				.orElseThrow(() -> new ApiException(ErrorCode.SAML_AUTHENTICATION_FAILED,
						"The institution this sign-in was started for no longer exists."));
		return new Institution(institutionRef.institutionId(), institutionRef.name());
	}

	/**
	 * Confirms Spring Security actually produced a SAML assertion here. Nothing in the assertion
	 * itself is read afterwards - see {@link SamlUserMapper} for why - this exists only so an
	 * authentication of the wrong kind is refused rather than silently treated as one.
	 */
	private void requireSamlAssertion(Authentication authentication) {
		if (!(authentication instanceof Saml2AssertionAuthentication)) {
			throw new ApiException(ErrorCode.SAML_AUTHENTICATION_FAILED,
					"This sign-in did not produce a SAML assertion.");
		}
	}

	/**
	 * What a completed sign-in produced.
	 *
	 * <p>{@code token} and {@code expiresAt} are the API Reference's sign-in envelope, now that
	 * TokenService exists. {@code institution} is prototype evidence that the right transaction
	 * was used - it would come out before this is shown to a real client. There is no identity
	 * evidence alongside it: {@code user.userId()} is an opaque device id, never a username or
	 * email, by design.
	 */
	public record SamlLoginResult(String token, Instant expiresAt, Instant serverTime,
			Institution institution, TnfUser user) {
	}
}
