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
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1logout/post/responses").propertyNames())
				.contains("200", "401", "403");
	}

	@Test
	void declaresBearerAuthenticationAndRequiresItOnlyWhereItApplies() throws Exception {
		JsonNode scheme = apiDocs().at("/components/securitySchemes/adminBearerAuth");

		assertThat(scheme.at("/type").asString()).isEqualTo("http");
		assertThat(scheme.at("/scheme").asString()).isEqualTo("bearer");
		assertThat(scheme.at("/bearerFormat").asString()).isEqualTo("JWT");

		JsonNode paths = apiDocs().get("paths");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1me/get/security").toString())
				.contains("adminBearerAuth");
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1logout/post/security").toString())
				.contains("adminBearerAuth");

		// The public endpoints must not advertise a bearer requirement.
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1login/post/security").isMissingNode()).isTrue();
		assertThat(paths.at("/~1api~1admin~1v1~1auth~1refresh/post/security").isMissingNode()).isTrue();
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
		JsonNode profileProperties = apiDocs().at("/components/schemas/AdminProfileResponse/properties");

		assertThat(profileProperties.propertyNames())
				.containsExactlyInAnyOrder("id", "email", "name", "role", "publisherId", "institutionId", "status");
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
