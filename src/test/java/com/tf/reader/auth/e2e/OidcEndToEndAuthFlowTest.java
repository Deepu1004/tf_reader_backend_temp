package com.tf.reader.auth.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;
import org.springframework.web.client.RestClient;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.MockOidcTestProfile;
import com.tf.reader.auth.AuthTestInstitutions;
import com.tf.reader.auth.AuthTestUsers;
import com.tf.reader.auth.repository.ReaderUserRepository;
import com.tf.reader.auth.saml.SamlAuthenticationService;
import com.tf.reader.auth.transaction.AuthTransactionStore;
import com.tf.reader.catalogue.repository.InstitutionRepository;

/**
 * The complete local OIDC flow, over real HTTP, with nothing stubbed.
 *
 * <pre>
 * POST /api/v1/auth/oidc/start {username, password}
 *   → back channel: POST {provider}/oauth2/token   (the password grant, client secret included)
 *   → ID token: JWKS signature, issuer, audience, expiry
 *   → ReaderUserDirectory.findOrProvisionIndividual
 *   → access + refresh token minted and stashed behind a one-time oidcTxnId
 *   ← {oidcTxnId, expiresAt, serverTime}
 * POST /api/v1/auth/oidc/token {oidcTxnId}
 *   ← {accessToken, refreshToken, expiresIn}
 * GET /api/v1/auth/me with the access token
 * </pre>
 *
 * <p>Unlike the old SAML-style flow this replaced, there is no browser redirect anywhere in this
 * path: both calls are plain JSON in, JSON out.
 */
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
				"spring.profiles.active=" })
class OidcEndToEndAuthFlowTest extends MockOidcTestProfile {

	private final RestClient http = RestClient.builder()
			.requestFactory(new JdkClientHttpRequestFactory(
					java.net.http.HttpClient.newBuilder()
							.followRedirects(java.net.http.HttpClient.Redirect.NEVER)
							.build()))
			.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> { })
			.build();

	@Autowired
	private SamlAuthenticationService samlAuthentication;

	@Autowired
	private AuthTransactionStore samlTransactions;

	@Autowired
	private InstitutionRepository institutions;

	@Autowired
	private ReaderUserRepository readerUsers;

	@BeforeEach
	void seedInstitutions() {
		AuthTestInstitutions.seed(institutions);
		// Only the Coexistence tests' SAML leg needs a pre-provisioned user - OIDC provisions its
		// own - but seeding it unconditionally here keeps this class self-contained regardless of
		// what ran before it against the same static Mongo container.
		AuthTestUsers.seed(readerUsers);
	}

	// ───────────────────────── the whole flow, end to end ─────────────────────────

	@Test
	void aLocalOidcSignInBecomesAnApplicationJwtThatWorksOnProtectedApis() {
		Map<String, Object> start = startSignIn(EMAIL, PASSWORD);

		assertThat(start.get("oidcTxnId").toString()).startsWith("authCode_");
		assertThat(start.get("expiresAt")).isNotNull();

		Map<String, Object> tokens = redeem((String) start.get("oidcTxnId"));
		assertThat(tokens.get("accessToken")).isNotNull();
		assertThat(tokens.get("refreshToken")).isNotNull();

		@SuppressWarnings("unchecked")
		Map<String, Object> me = get("/api/v1/auth/me", Map.class, (String) tokens.get("accessToken"));

		assertThat(me).containsEntry("type", "INDIVIDUAL");
		assertThat(me).doesNotContainKey("institutionId");
		assertThat(asList(me.get("roles"))).containsExactly("SUBSCRIBER");
	}

	@Test
	void theSameEmailResolvesToTheSameIndividualOnASecondSignIn() {
		// The mock always authenticates john.doe@example.com, so two runs through the whole flow
		// prove auto-provisioning is idempotent regardless of what ran before this test.
		String firstUserId = userIdFor(redeemedAccessToken());
		String secondUserId = userIdFor(redeemedAccessToken());

		assertThat(secondUserId).isEqualTo(firstUserId);
	}

	@Test
	void anOidcRefreshTokenDrivesRefreshAndLogoutLikeAnyOther() {
		Map<String, Object> tokens = redeem((String) startSignIn(EMAIL, PASSWORD).get("oidcTxnId"));
		String refreshToken = (String) tokens.get("refreshToken");

		@SuppressWarnings("unchecked")
		Map<String, Object> refreshed = http.post().uri(uri("/api/v1/auth/refresh"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"refreshToken\":\"" + refreshToken + "\"}")
				.retrieve().body(Map.class);
		assertThat(refreshed.get("accessToken")).isNotNull();
		assertThat(refreshed.get("refreshToken")).isNotNull().isNotEqualTo(refreshToken);

		int logoutStatus = http.post().uri(uri("/api/v1/auth/logout"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"refreshToken\":\"" + refreshed.get("refreshToken") + "\"}")
				.retrieve().toBodilessEntity().getStatusCode().value();
		assertThat(logoutStatus).isEqualTo(204);
	}

	// ───────────────────────────── the failure matrix ─────────────────────────────

	@Nested
	class Failures {

		@Test
		void aWrongPasswordIsRefused() {
			assertThat(startStatus(EMAIL, "not-the-password")).isEqualTo(401);
		}

		@Test
		void anUnknownUsernameIsRefusedTheSameWayAWrongPasswordIs() {
			// Same status and code as a wrong password - RFC 6749 §5.2's own reasoning applies here
			// too: distinguishing "no such user" from "wrong password" tells an attacker whether a
			// guessed address exists.
			assertThat(startStatus("nobody@example.com", PASSWORD)).isEqualTo(401);
		}

		@Test
		void redeemingAnUnknownOidcTxnIdIsRefused() {
			assertThat(redeemStatus("never-issued")).isEqualTo(401);
		}

		@Test
		void theSameOidcTxnIdCannotBeRedeemedTwice() {
			String oidcTxnId = (String) startSignIn(EMAIL, PASSWORD).get("oidcTxnId");

			assertThat(redeemStatus(oidcTxnId)).isEqualTo(200);
			assertThat(redeemStatus(oidcTxnId)).isEqualTo(401);
		}
	}

	// ───────────────────── SAML and OIDC in the same application ─────────────────────

	@Nested
	class Coexistence {

		@Test
		void aSamlSignInAndAnOidcSignInResolveIndependently() {
			var viaSaml = samlAuthentication.complete(samlAuthentication("john.doe@example.com"),
					samlTransactions.open(AuthTestInstitutions.UCL).id());
			String viaOidc = userIdFor(redeemedAccessToken());

			// Different accounts entirely: one is an institution membership, the other has none.
			assertThat(viaOidc).isNotEqualTo(viaSaml.user().userId());
			assertThat(viaSaml.user().institutionId()).isEqualTo(AuthTestInstitutions.UCL);
		}

		@Test
		void theSamlLegIsUntouchedByAnyOfThis() {
			// The SAML entry point still redirects to its own IdP, carrying its own RelayState.
			String txn = samlTransactions.open(AuthTestInstitutions.UCL).id();

			String location = http.get()
					.uri(uri("/saml2/authenticate?registrationId=tf-reader&authTxn=" + txn))
					.retrieve()
					.toBodilessEntity()
					.getHeaders()
					.getFirst("Location");

			assertThat(java.net.URLDecoder.decode(location, java.nio.charset.StandardCharsets.UTF_8))
					.startsWith("https://samlmock.dev/idp")
					.contains("RelayState=" + txn);
		}
	}

	// ───────────────────────── driving the flow ─────────────────────────

	private static final String EMAIL = "john.doe@example.com";

	@SuppressWarnings("unchecked")
	private Map<String, Object> startSignIn(String username, String password) {
		return http.post().uri(uri("/api/v1/auth/oidc/start"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
				.retrieve().body(Map.class);
	}

	private int startStatus(String username, String password) {
		return http.post().uri(uri("/api/v1/auth/oidc/start"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
				.retrieve().toBodilessEntity().getStatusCode().value();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> redeem(String oidcTxnId) {
		return http.post().uri(uri("/api/v1/auth/oidc/token"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"oidcTxnId\":\"" + oidcTxnId + "\"}")
				.retrieve().body(Map.class);
	}

	private int redeemStatus(String oidcTxnId) {
		return http.post().uri(uri("/api/v1/auth/oidc/token"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"oidcTxnId\":\"" + oidcTxnId + "\"}")
				.retrieve().toBodilessEntity().getStatusCode().value();
	}

	private String redeemedAccessToken() {
		Map<String, Object> tokens = redeem((String) startSignIn(EMAIL, PASSWORD).get("oidcTxnId"));
		return (String) tokens.get("accessToken");
	}

	private String userIdFor(String accessToken) {
		@SuppressWarnings("unchecked")
		Map<String, Object> me = get("/api/v1/auth/me", Map.class, accessToken);
		return (String) me.get("userId");
	}

	// ───────────────────────────── plumbing ─────────────────────────────

	private static java.net.URI uri(String url) {
		return java.net.URI.create(url.startsWith("http") ? url : baseUrl() + url);
	}

	private <T> T get(String url, Class<T> type, String bearer) {
		return http.get().uri(uri(url))
				.headers(headers -> {
					if (bearer != null) {
						headers.setBearerAuth(bearer);
					}
				})
				.retrieve().body(type);
	}

	@SuppressWarnings("unchecked")
	private static List<String> asList(Object value) {
		return (List<String>) value;
	}

	private static Authentication samlAuthentication(String email) {
		Saml2ResponseAssertionAccessor assertion = new StubAssertion(email, Map.of(
				"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
				List.of(email)));
		return new Saml2AssertionAuthentication(assertion, List.of(), "tf-reader");
	}

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
