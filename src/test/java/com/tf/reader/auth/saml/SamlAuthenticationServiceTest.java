package com.tf.reader.auth.saml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;

import com.tf.reader.auth.repository.ReaderSessionRepository;
import com.tf.reader.auth.security.TnfJwtValidator;
import com.tf.reader.auth.saml.SamlAuthenticationService.SamlLoginResult;
import com.tf.reader.auth.token.JwtTokenService;
import com.tf.reader.auth.transaction.AuthTransaction;
import com.tf.reader.auth.transaction.AuthTransactionStore;
import com.tf.reader.catalogue.api.InstitutionRef;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * The join between "Spring Security validated a SAML assertion" and "which institution our
 * backend chose for this sign-in" - and nothing about who the assertion says signed in, which
 * this service never reads.
 *
 * <p>No servlet, no network, no samlmock.dev - the service deliberately knows nothing about
 * HTTP, which is what makes this testable at this level.
 */
class SamlAuthenticationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-13T09:00:00Z");
	private static final String SECRET = "a-test-only-signing-secret-of-sufficient-length-0123456789";
	private static final int MAX_SEATS = 50;

	private final AuthTransactionStore transactions =
			new AuthTransactionStore(Clock.fixed(NOW, ZoneOffset.UTC));

	private static final Map<String, InstitutionRef> INSTITUTIONS = Map.of(
			"inst_7f3", new InstitutionRef("inst_7f3", "Imperial College London"),
			"inst_ucl", new InstitutionRef("inst_ucl", "University College London"));

	private final ReaderSessionRepository readerSessions = mock(ReaderSessionRepository.class);

	private final SamlAuthenticationService service = new SamlAuthenticationService(transactions,
			institutionId -> Optional.ofNullable(INSTITUTIONS.get(institutionId)),
			new SamlUserMapper(readerSessions, Clock.fixed(NOW, ZoneOffset.UTC), MAX_SEATS),
			JwtTokenService.forTest(SECRET, java.time.Duration.ofHours(1),
					Clock.fixed(NOW, ZoneOffset.UTC)),
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void completesASignInForTheInstitutionTheTransactionWasOpenedFor() {
		AuthTransaction transaction = transactions.open("inst_7f3");

		SamlLoginResult result = service.complete(samlAuthentication(), transaction.id());

		assertThat(result.institution().institutionId()).isEqualTo("inst_7f3");
		assertThat(result.institution().name()).isEqualTo("Imperial College London");
		assertThat(result.user().userId()).startsWith("dev_");
		assertThat(result.user().institutionId()).isEqualTo("inst_7f3");
		assertThat(result.serverTime()).isEqualTo(NOW);
	}

	@Test
	void twoDevicesSigningInGetIndependentIdentitiesEvenAtTheSameInstitution() {
		// No directory, no email, no username - two sign-ins with no deviceId of their own are
		// simply two different devices.
		SamlLoginResult first = service.complete(samlAuthentication(), transactions.open("inst_7f3").id());
		SamlLoginResult second = service.complete(samlAuthentication(), transactions.open("inst_7f3").id());

		assertThat(first.user().userId()).isNotEqualTo(second.user().userId());
		assertThat(first.user().institutionId()).isEqualTo(second.user().institutionId());
	}

	@Test
	void aDevicePresentingItsOwnIdKeepsItAtCompletion() throws Exception {
		AuthTransaction transaction = transactions.open("inst_7f3", null, "dev_returning");

		SamlLoginResult result = service.complete(samlAuthentication(), transaction.id());

		assertThat(result.user().userId()).isEqualTo("dev_returning");
	}

	@Test
	void refusesANewDeviceWhenTheInstitutionHasNoSeatFree() {
		when(readerSessions.countByInstitutionIdAndRevokedAtIsNullAndExpiresAtAfter("inst_7f3", NOW))
				.thenReturn((long) MAX_SEATS);
		AuthTransaction transaction = transactions.open("inst_7f3");

		assertThatThrownBy(() -> service.complete(samlAuthentication(), transaction.id()))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).getCode())
				.isEqualTo(ErrorCode.SEAT_LIMIT_REACHED);
	}

	@Test
	void refusesARelayStateWeNeverIssued() {
		assertThatThrownBy(() -> service.complete(samlAuthentication(), "authTxn_invented"))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).getCode())
				.isEqualTo(ErrorCode.SAML_AUTHENTICATION_FAILED);
	}

	@Test
	void refusesAMissingRelayState() {
		// Without a transaction there is no institution, and guessing one would mean signing
		// somebody in to an institution nobody selected.
		Authentication authentication = samlAuthentication();

		assertThatThrownBy(() -> service.complete(authentication, null))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).getCode())
				.isEqualTo(ErrorCode.SAML_AUTHENTICATION_FAILED);
	}

	@Test
	void refusesAReplayedRelayState() {
		AuthTransaction transaction = transactions.open("inst_7f3");
		Authentication authentication = samlAuthentication();
		service.complete(authentication, transaction.id());

		assertThatThrownBy(() -> service.complete(authentication, transaction.id()))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).getCode())
				.isEqualTo(ErrorCode.SAML_AUTHENTICATION_FAILED);
	}

	@Test
	void refusesAnAuthenticationThatCarriesNoSamlAssertion() {
		Authentication notSaml = new UsernamePasswordAuthenticationToken("john", "secret");
		String relayState = transactions.open("inst_7f3").id();

		assertThatThrownBy(() -> service.complete(notSaml, relayState))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).getCode())
				.isEqualTo(ErrorCode.SAML_AUTHENTICATION_FAILED);
	}

	@Test
	void carriesATokenMintedFromTheMappedUser() {
		// The token is issued from the mapped user, after mapping succeeded - so a refused
		// mapping can never produce one, and the token can never disagree with the user beside it.
		SamlLoginResult result = service.complete(samlAuthentication(), transactions.open("inst_ucl").id());

		Jwt jwt = decoderAtTheTestsClock().decode(result.token());

		assertThat(jwt.getClaimAsString("userId")).isEqualTo(result.user().userId());
		assertThat(jwt.getClaimAsString("institutionId")).isEqualTo("inst_ucl");
		assertThat(jwt.getExpiresAt()).isEqualTo(result.expiresAt());
	}

	/**
	 * A decoder that judges expiry by the same clock the token was minted with.
	 *
	 * <p>{@code NimbusJwtDecoder} installs Spring's default validator chain, and that chain reads
	 * the <b>system</b> clock. A token this test mints at a fixed instant therefore expires against
	 * wall-clock time, so the test passed for one hour after that instant and failed forever
	 * afterwards - reported as "Jwt expired", which reads like a production bug rather than a
	 * test that pinned itself to a date. Everything here is fixed-clock, so the verifier must be
	 * too.
	 */
	private JwtDecoder decoderAtTheTestsClock() {
		byte[] keyBytes = SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var signingKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256");
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
						.macAlgorithm(MacAlgorithm.HS256)
						.build();
		decoder.setJwtValidator(new TnfJwtValidator(Clock.fixed(NOW, ZoneOffset.UTC)));
		return decoder;
	}

	private Authentication samlAuthentication() {
		Saml2ResponseAssertionAccessor assertion = new StubAssertion("irrelevant-subject", Map.of());
		return new Saml2AssertionAuthentication(assertion, List.of(), "tf-reader");
	}

	/** Stands in for an assertion Spring Security has already validated - its content is never read. */
	private record StubAssertion(String nameId, Map<String, List<Object>> attributes)
			implements Saml2ResponseAssertionAccessor {

		@Override
		public String getNameId() {
			return nameId;
		}

		@Override
		public List<String> getSessionIndexes() {
			return List.of();
		}

		@Override
		public Map<String, List<Object>> getAttributes() {
			return attributes;
		}

		@Override
		public String getResponseValue() {
			return "";
		}
	}
}
