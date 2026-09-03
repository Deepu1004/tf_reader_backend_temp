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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.MockOidcTestProfile;
import com.tf.reader.auth.AuthTestInstitutions;
import com.tf.reader.auth.AuthTestUsers;
import com.tf.reader.auth.repository.ReaderUserRepository;
import com.tf.reader.auth.saml.SamlAuthenticationService;
import com.tf.reader.auth.token.AuthorizationCodeStore;
import com.tf.reader.auth.transaction.AuthTransactionStore;
import com.tf.reader.catalogue.repository.InstitutionRepository;

/**
 * The complete local OIDC flow, over real HTTP, with nothing stubbed.
 *
 * <pre>
 * POST /api/v1/auth/oidc/start (no body - no institution to name)
 *   → authorizationUrl
 *   → GET  {provider}/oauth2/authorize        (the sign-in page)
 *   → POST {provider}/oauth2/authorize        (the "Login &amp; Authorize" button)
 *   → 302  /api/v1/auth/oidc/callback?code=…&amp;state=…
 *   → back channel: POST {provider}/oauth2/token   (code + client secret)
 *   → ID token: JWKS signature, issuer, audience, expiry, nonce
 *   → ReaderUserDirectory.findOrProvisionIndividual
 *   → refresh token + one-time code, 302 to tfreader://auth/callback?code=…
 *   → POST /api/v1/auth/token redeems the code for the real token pair
 *   → GET /api/v1/auth/me with the access token
 * </pre>
 *
 * <p>The callback here is never read as a JSON body: it is a browser redirect, exactly like the
 * SAML ACS, so this test follows it by hand the same way {@code SamlLoginFlowTest} does.
 */
// spring.profiles.active is forced empty because application.yml defaults it to "local", and a
// developer's own gitignored application-local.yml (never committed - see CLAUDE.md) points the
// mock SAML registration this class's Coexistence checks at their own machine instead of
// samlmock.dev. The OIDC side of this class is unaffected: MockOidcTestProfile configures it via
// @DynamicPropertySource, not profile-specific YAML.
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = { "tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
				"spring.profiles.active=" })
class OidcEndToEndAuthFlowTest extends MockOidcTestProfile {

	private static final String CODE_PREFIX =
			AuthorizationCodeStore.DEEP_LINK_CALLBACK + "?code=";

	private static final String ERROR_DEEP_LINK =
			AuthorizationCodeStore.DEEP_LINK_CALLBACK + "?error=OIDC_AUTHENTICATION_FAILED";

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
		Map<String, Object> start = startSignIn();

		assertThat(start.get("authTxnId").toString()).startsWith("oidcTxn_");
		assertThat(start).doesNotContainKey("institution");

		String authorizationUrl = (String) start.get("authorizationUrl");
		assertThat(authorizationUrl).startsWith(ISSUER + "/oauth2/authorize");
		assertThat(authorizationUrl).contains("state=").contains("nonce=");

		String page = get(authorizationUrl, String.class);
		assertThat(page).contains("Local Mock OIDC").contains("john.doe@example.com");

		String callback = authorize(authorizationUrl);
		assertThat(callback).startsWith(REDIRECT_URI).contains("code=").contains("state=");

		String deepLink = redirectLocation(callback);
		assertThat(deepLink).startsWith(CODE_PREFIX);

		Map<String, Object> tokens = exchangeCode(codeFrom(deepLink));
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
		Map<String, Object> firstTokens = exchangeCode(codeFrom(redirectLocation(callbackUrlFor())));
		Map<String, Object> secondTokens = exchangeCode(codeFrom(redirectLocation(callbackUrlFor())));

		String firstUserId = userIdFor((String) firstTokens.get("accessToken"));
		String secondUserId = userIdFor((String) secondTokens.get("accessToken"));

		assertThat(secondUserId).isEqualTo(firstUserId);
	}

	@Test
	void anOidcRefreshTokenDrivesRefreshAndLogoutLikeAnyOther() {
		Map<String, Object> tokens = exchangeCode(codeFrom(redirectLocation(callbackUrlFor())));
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
		void aCallbackWithAStateWeNeverIssuedRedirectsWithAnError() {
			String deepLink = redirectLocation(REDIRECT_URI + "?code=made-up&state=never-issued");

			assertThat(deepLink).isEqualTo(ERROR_DEEP_LINK);
		}

		@Test
		void aCallbackWithAValidStateButNoCodeRedirectsWithAnError() {
			String state = queryParam((String) startSignIn().get("authorizationUrl"), "state");

			assertThat(redirectLocation(REDIRECT_URI + "?state=" + state))
					.isEqualTo(ERROR_DEEP_LINK);
		}

		@Test
		void theSameCallbackCannotBeUsedTwice() {
			String callback = callbackUrlFor();

			assertThat(redirectLocation(callback)).startsWith(CODE_PREFIX);
			assertThat(redirectLocation(callback))
					.isEqualTo(ERROR_DEEP_LINK);
		}

		@Test
		void aProviderErrorIsNotPassedThroughToTheClient() {
			String deepLink = redirectLocation(
					REDIRECT_URI + "?error=access_denied&error_description=THE-USER-CANCELLED&state=whatever");

			assertThat(deepLink).isEqualTo(ERROR_DEEP_LINK);
			assertThat(deepLink).doesNotContain("THE-USER-CANCELLED").doesNotContain("access_denied");
		}
	}

	// ───────────────────── SAML and OIDC in the same application ─────────────────────

	@Nested
	class Coexistence {

		@Test
		void aSamlSignInAndAnOidcSignInResolveIndependently() {
			var viaSaml = samlAuthentication.complete(samlAuthentication("john.doe@example.com"),
					samlTransactions.open(AuthTestInstitutions.UCL).id());
			Map<String, Object> tokens = exchangeCode(codeFrom(redirectLocation(callbackUrlFor())));
			String viaOidc = userIdFor((String) tokens.get("accessToken"));

			// Different accounts entirely: one is an institution membership, the other has none.
			assertThat(viaOidc).isNotEqualTo(viaSaml.user().userId());
			assertThat(viaSaml.user().institutionId()).isEqualTo(AuthTestInstitutions.UCL);
		}

		@Test
		void theTwoTransactionStoresDoNotShareIds() {
			// A state minted for one flow must be meaningless to the other's callback.
			String samlTxn = samlTransactions.open(AuthTestInstitutions.UCL).id();

			assertThat(redirectLocation(REDIRECT_URI + "?code=x&state=" + samlTxn))
					.isEqualTo(ERROR_DEEP_LINK);
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

	@SuppressWarnings("unchecked")
	private Map<String, Object> startSignIn() {
		return http.post().uri(uri("/api/v1/auth/oidc/start"))
				.retrieve().body(Map.class);
	}

	/** Everything up to, but not including, the callback. Returns the callback url. */
	private String callbackUrlFor() {
		return authorize((String) startSignIn().get("authorizationUrl"));
	}

	/** GETs a callback url without following the redirect, returning its {@code Location}. */
	private String redirectLocation(String url) {
		return http.get().uri(uri(url)).retrieve().toBodilessEntity().getHeaders().getFirst("Location");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> exchangeCode(String code) {
		return http.post().uri(uri("/api/v1/auth/token"))
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"" + code + "\"}")
				.retrieve().body(Map.class);
	}

	private String userIdFor(String accessToken) {
		@SuppressWarnings("unchecked")
		Map<String, Object> me = get("/api/v1/auth/me", Map.class, accessToken);
		return (String) me.get("userId");
	}

	/** Presses "Login &amp; Authorize" and returns the url the provider redirects to. */
	private String authorize(String authorizationUrl) {
		MultiValueMap<String, String> params = UriComponentsBuilder.fromUriString(authorizationUrl)
				.build().getQueryParams();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		for (String name : List.of("client_id", "redirect_uri", "response_type", "scope", "state",
				"nonce")) {
			form.add(name, java.net.URLDecoder.decode(params.getFirst(name),
					java.nio.charset.StandardCharsets.UTF_8));
		}

		return http.post()
				.uri(uri("/oauth2/authorize"))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.toBodilessEntity()
				.getHeaders()
				.getFirst("Location");
	}

	// ───────────────────────────── plumbing ─────────────────────────────

	private static java.net.URI uri(String url) {
		return java.net.URI.create(url.startsWith("http") ? url : baseUrl() + url);
	}

	private <T> T get(String url, Class<T> type) {
		return http.get().uri(uri(url)).retrieve().body(type);
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

	/** The success redirect carries exactly one query param, so a substring is exact and safe -
	 * the same approach {@code SamlAuthenticationSuccessHandlerTest} uses, rather than parsing
	 * a custom URI scheme with {@link UriComponentsBuilder}. */
	private static String codeFrom(String deepLink) {
		return deepLink.substring(CODE_PREFIX.length());
	}

	private static String queryParam(String url, String name) {
		return java.net.URLDecoder.decode(
				UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name),
				java.nio.charset.StandardCharsets.UTF_8);
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
