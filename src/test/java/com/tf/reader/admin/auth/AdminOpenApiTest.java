package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The generated documentation under the dev profile.
 *
 * <p>Asserts both that the four real endpoints are described and that nothing sensitive leaks into
 * the schema.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class AdminOpenApiTest {

	private static final String API_DOCS_PATH = "/v3/api-docs";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void servesTheOpenApiDocument() throws Exception {
		MvcResult result = this.mockMvc.perform(get(API_DOCS_PATH)).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(apiDocs()).isNotNull();
		assertThat(apiDocs().at("/openapi").asString()).startsWith("3.");
	}

	@Test
	void servesSwaggerUi() throws Exception {
		// springdoc redirects the configured path onto the bundled UI resources.
		int status = this.mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus();

		assertThat(status).isIn(200, 302);
	}

	@Test
	void documentsExactlyTheFourRealAuthEndpoints() throws Exception {
		JsonNode paths = apiDocs().get("paths");

		assertThat(paths.get("/api/admin/v1/auth/login").has("post")).isTrue();
		assertThat(paths.get("/api/admin/v1/auth/refresh").has("post")).isTrue();
		assertThat(paths.get("/api/admin/v1/auth/logout").has("post")).isTrue();
		assertThat(paths.get("/api/admin/v1/auth/me").has("get")).isTrue();

		assertThat(paths.propertyNames()).containsExactlyInAnyOrder(
				"/api/admin/v1/auth/login",
				"/api/admin/v1/auth/refresh",
				"/api/admin/v1/auth/logout",
				"/api/admin/v1/auth/me");
	}

	@Test
	void documentsTheResponseCodesForEachEndpoint() throws Exception {
		JsonNode paths = apiDocs().get("paths");

		assertThat(paths.at("/~1api~1admin~1v1~1auth~1login/post/responses").propertyNames())
				.contains("200", "400", "401");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1refresh/post/responses").propertyNames())
				.contains("200", "400", "401");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1me/get/responses").propertyNames())
				.contains("200", "401", "403");

		// The contract's logout is 204 with no body, and never 401: it is a public endpoint.
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1logout/post/responses").propertyNames())
				.contains("204", "400")
				.doesNotContain("200", "401");
	}

	/** The scheme is named {@code adminToken} because that is the name the contract gives it. */
	@Test
	void declaresBearerAuthenticationAndRequiresItOnlyWhereItApplies() throws Exception {
		JsonNode scheme = apiDocs().at("/components/securitySchemes/adminToken");

		assertThat(scheme.at("/type").asString()).isEqualTo("http");
		assertThat(scheme.at("/scheme").asString()).isEqualTo("bearer");
		assertThat(scheme.at("/bearerFormat").asString()).isEqualTo("JWT");

		JsonNode paths = apiDocs().get("paths");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1me/get/security").toString())
				.contains("adminToken");

		// The three public endpoints must not advertise a bearer requirement. Logout is one of them:
		// its credential is the refresh token in the body.
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1login/post/security").isMissingNode()).isTrue();
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1refresh/post/security").isMissingNode()).isTrue();
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1logout/post/security").isMissingNode()).isTrue();
	}

	@Test
	void neverExposesThePasswordHashOrInternalFields() throws Exception {
		String document = apiDocs().toString();

		assertThat(document).doesNotContain("passwordHash");
		assertThat(document).doesNotContain("currentRefreshTokenHash");
		assertThat(document).doesNotContain("revokedReason");
	}

	@Test
	void documentsTheAdminProfileWithoutCredentialFields() throws Exception {
		JsonNode profileProperties = apiDocs().at("/components/schemas/AdminUser/properties");

		assertThat(profileProperties.propertyNames()).containsExactlyInAnyOrder("id", "email", "name", "role",
				"scopePublisherId", "scopeInstitutionId", "status");
	}

	/** The generated schemas must carry the names and fields the published contract uses. */
	@Test
	void documentsTheContractsAuthSchemas() throws Exception {
		JsonNode schemas = apiDocs().at("/components/schemas");

		assertThat(schemas.at("/AdminLoginRequest/properties").propertyNames())
				.containsExactlyInAnyOrder("email", "password");
		assertThat(schemas.at("/RefreshRequest/properties").propertyNames())
				.containsExactlyInAnyOrder("refreshToken");

		assertThat(schemas.at("/TokenPair/properties").propertyNames())
				.containsExactlyInAnyOrder("accessToken", "expiresIn", "refreshToken", "refreshExpiresIn");
		assertThat(schemas.at("/AdminLoginResponse/properties").propertyNames())
				.containsExactlyInAnyOrder("accessToken", "expiresIn", "refreshToken", "refreshExpiresIn",
						"user");

		// tokenType was never in the contract.
		assertThat(schemas.at("/AdminLoginResponse/properties").propertyNames()).doesNotContain("tokenType");
		assertThat(schemas.at("/TokenPair/properties").propertyNames()).doesNotContain("tokenType");
	}

	@Test
	void documentsTheContractsErrorEnvelope() throws Exception {
		JsonNode error = apiDocs().at("/components/schemas/Error");

		assertThat(error.at("/properties").propertyNames())
				.containsExactlyInAnyOrder("timestamp", "status", "code", "message", "path");

		// springdoc inlines the code enum into the property rather than emitting a separate component;
		// the vocabulary is what the contract pins down, and it is all here.
		assertThat(error.at("/properties/code/enum").toString())
				.contains("UNAUTHENTICATED", "FORBIDDEN_SCOPE", "FORBIDDEN_INSTITUTION_MISMATCH",
						"VALIDATION_FAILED", "NOT_FOUND", "STALE_VERSION");

		// Every documented error response points at that one envelope, as JSON.
		JsonNode paths = apiDocs().get("paths");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1login/post/responses/401/content/application~1json/schema")
				.toString()).contains("Error");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1refresh/post/responses/401/content/application~1json/schema")
				.toString()).contains("Error");
	}

	/** The login response is the one place the admin's own record is embedded. */
	@Test
	void documentsTheLoginResponseUserAsTheAdminUserSchema() throws Exception {
		assertThat(apiDocs().at("/components/schemas/AdminLoginResponse/properties/user").toString())
				.contains("AdminUser");
	}

	@Test
	void doesNotDocumentActuatorOrOtherInternalEndpoints() throws Exception {
		assertThat(apiDocs().get("paths").propertyNames())
				.noneMatch(path -> path.startsWith("/actuator"))
				.noneMatch(path -> path.startsWith("/error"));
	}

	private JsonNode apiDocs() throws Exception {
		MvcResult result = this.mockMvc.perform(get(API_DOCS_PATH)).andReturn();
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
