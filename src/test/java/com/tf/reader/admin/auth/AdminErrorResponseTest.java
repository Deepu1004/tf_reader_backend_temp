package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;

import tools.jackson.databind.JsonNode;

/**
 * Every failure must use the one error envelope the contract defines, as {@code application/json}.
 *
 * <p>Two code paths produce errors and both are checked here: the {@code @RestControllerAdvice} for
 * failures raised inside a controller, and the security filter chain for requests rejected before
 * routing. A client cannot tell them apart, which is the point.
 */
class AdminErrorResponseTest extends AbstractAdminAuthIntegrationTest {

	private static final String LOGIN_PATH_VALUE = "/api/admin/v1/auth/login";
	private static final String ME_PATH_VALUE = "/api/admin/v1/auth/me";

	@Test
	void rendersAFailedLoginAsTheContractsErrorEnvelope() throws Exception {
		saveAdmin("err@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = performLogin("err@tandf.example", "wrong-password");

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");

		JsonNode error = bodyAsJson(result);
		assertThat(error.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "code", "message",
				"path");
		assertThat(error.get("status").asInt()).isEqualTo(401);
		assertThat(error.get("code").asString()).isEqualTo("UNAUTHENTICATED");
		assertThat(error.get("message").asString()).isNotBlank();
		assertThat(error.get("path").asString()).isEqualTo(LOGIN_PATH_VALUE);
		assertThat(Instant.parse(error.get("timestamp").asString())).isNotNull();
	}

	/** The old RFC 9457 shape must be gone: no problem+json, and none of its field names. */
	@Test
	void noLongerReturnsAProblemDetail() throws Exception {
		MvcResult result = performLogin("ghost@tandf.example", PASSWORD);

		assertThat(result.getResponse().getContentType()).doesNotContain("problem");
		assertThat(result.getResponse().getContentAsString())
				.doesNotContain("\"type\"")
				.doesNotContain("\"title\"")
				.doesNotContain("\"detail\"")
				.doesNotContain("\"instance\"");
	}

	/**
	 * A request rejected by the filter chain never reaches Spring MVC, so this asserts the second,
	 * independent rendering path produces the same envelope.
	 */
	@Test
	void rendersAFilterChainRejectionAsTheSameEnvelope() throws Exception {
		MvcResult result = this.mockMvc
				.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ME_PATH))
				.andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(401);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");

		JsonNode error = bodyAsJson(result);
		assertThat(error.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "code", "message",
				"path");
		assertThat(error.get("code").asString()).isEqualTo("UNAUTHENTICATED");
		assertThat(error.get("status").asInt()).isEqualTo(401);
		assertThat(error.get("path").asString()).isEqualTo(ME_PATH_VALUE);
	}

	@Test
	void rendersAValidationFailureAsValidationFailed() throws Exception {
		MvcResult result = performLogin("not-an-email", PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(400);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");

		JsonNode error = bodyAsJson(result);
		assertThat(error.propertyNames()).containsExactlyInAnyOrder("timestamp", "status", "code", "message",
				"path");
		assertThat(error.get("code").asString()).isEqualTo("VALIDATION_FAILED");
		assertThat(error.get("status").asInt()).isEqualTo(400);
		assertThat(error.get("path").asString()).isEqualTo(LOGIN_PATH_VALUE);
	}

	@Test
	void rendersAnUnusableRefreshTokenAsUnauthenticated() throws Exception {
		MvcResult result = callRefresh("not-a-real-token");

		assertThat(result.getResponse().getStatus()).isEqualTo(401);

		JsonNode error = bodyAsJson(result);
		assertThat(error.get("code").asString()).isEqualTo("UNAUTHENTICATED");
		assertThat(error.get("path").asString()).isEqualTo("/api/admin/v1/auth/refresh");
	}

	/**
	 * The message must not vary with the cause. Whichever way authentication failed, the body is the
	 * same once the clock is discounted, so it cannot be used to probe account or token state.
	 */
	@Test
	void usesOneOpaqueMessageForEveryAuthenticationFailure() throws Exception {
		saveAdmin("errsame@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		String wrongPassword = withoutTimestamp(performLogin("errsame@tandf.example", "nope"));
		String unknownEmail = withoutTimestamp(performLogin("nobody@tandf.example", PASSWORD));

		assertThat(wrongPassword).isEqualTo(unknownEmail);
		assertThat(wrongPassword).doesNotContainIgnoringCase("suspend", "disabl", "exist", "password");
	}

	private String withoutTimestamp(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString().replaceAll(",?\"timestamp\":\"[^\"]*\"", "");
	}

	private JsonNode bodyAsJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
