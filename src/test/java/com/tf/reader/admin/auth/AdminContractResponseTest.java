package com.tf.reader.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;

import tools.jackson.databind.JsonNode;

/**
 * Asserts the JSON on the wire against the published API contract, field by field.
 *
 * <p>Separate from the behavioural tests on purpose: these fail when a response shape drifts from the
 * contract even though the flow still works, which is exactly the drift that is otherwise noticed
 * only by the client team.
 */
class AdminContractResponseTest extends AbstractAdminAuthIntegrationTest {

	@Test
	void loginReturnsTheTokenPairPlusTheSignedInAdmin() throws Exception {
		saveAdmin("shape@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		MvcResult result = performLogin("shape@tandf.example", PASSWORD);

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");

		assertThat(bodyAsJson(result).propertyNames()).containsExactlyInAnyOrder("accessToken", "expiresIn",
				"refreshToken", "refreshExpiresIn", "user");
	}

	@Test
	void loginUserCarriesTheContractsAdminUserFields() throws Exception {
		AdminUser admin = saveAdmin("shapeuser@tandf.example", AdminRole.PUBLISHER_ADMIN, AdminStatus.ACTIVE,
				"publisher-42", null);

		JsonNode user = bodyAsJson(performLogin("shapeuser@tandf.example", PASSWORD)).get("user");

		assertThat(user.propertyNames()).containsExactlyInAnyOrder("id", "email", "name", "role",
				"scopePublisherId", "scopeInstitutionId", "status");

		assertThat(user.get("id").asString()).isEqualTo(admin.getId());
		assertThat(user.get("email").asString()).isEqualTo("shapeuser@tandf.example");
		assertThat(user.get("role").asString()).isEqualTo("PUBLISHER_ADMIN");
		assertThat(user.get("scopePublisherId").asString()).isEqualTo("publisher-42");
		assertThat(user.get("scopeInstitutionId").isNull()).isTrue();
		assertThat(user.get("status").asString()).isEqualTo("ACTIVE");
	}

	/** The contract is explicit that a login never returns the password hash. */
	@Test
	void loginNeverReturnsThePasswordHash() throws Exception {
		AdminUser admin = saveAdmin("shapehash@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		String body = performLogin("shapehash@tandf.example", PASSWORD).getResponse().getContentAsString();

		assertThat(body).doesNotContain("passwordHash").doesNotContain(admin.getPasswordHash());
	}

	/** Refreshing proves nothing new about who the caller is, so it returns no user. */
	@Test
	void refreshReturnsTheTokenPairAndNoUser() throws Exception {
		saveAdmin("shaperefresh@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("shaperefresh@tandf.example");

		MvcResult result = callRefresh(tokens.refreshToken());

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");
		assertThat(bodyAsJson(result).propertyNames()).containsExactlyInAnyOrder("accessToken", "expiresIn",
				"refreshToken", "refreshExpiresIn");
	}

	/** The contract defines refreshToken as opaque: a string with nothing encoded in it. */
	@Test
	void issuesAnOpaqueRefreshTokenAsTheContractRequires() throws Exception {
		saveAdmin("shapeopaque@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		JsonNode login = bodyAsJson(performLogin("shapeopaque@tandf.example", PASSWORD));
		String refreshToken = login.get("refreshToken").asString();

		assertThat(login.get("refreshToken").isString()).isTrue();
		assertThat(refreshToken).doesNotContain(".").doesNotStartWith("eyJ");

		// The access token, by contrast, is still a JWT.
		assertThat(login.get("accessToken").asString()).contains(".").startsWith("eyJ");

		assertThat(bodyAsJson(callRefresh(refreshToken)).get("refreshToken").asString())
				.doesNotContain(".").doesNotStartWith("eyJ");
	}

	/** {@code tokenType} was in the implementation but never in the contract. */
	@Test
	void neitherTokenResponseCarriesATokenType() throws Exception {
		saveAdmin("shapetype@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("shapetype@tandf.example");

		assertThat(performLogin("shapetype@tandf.example", PASSWORD).getResponse().getContentAsString())
				.doesNotContain("tokenType").doesNotContain("Bearer");
		assertThat(callRefresh(tokens.refreshToken()).getResponse().getContentAsString())
				.doesNotContain("tokenType").doesNotContain("Bearer");
	}

	@Test
	void reportsTwelveHoursOfRefreshLifetimeOnLogin() throws Exception {
		saveAdmin("shapettl@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);

		JsonNode body = bodyAsJson(performLogin("shapettl@tandf.example", PASSWORD));

		assertThat(body.get("refreshExpiresIn").asLong()).isEqualTo(43_200L);
		assertThat(body.get("refreshExpiresIn").asLong())
				.isEqualTo(Duration.ofHours(12).toSeconds());
		assertThat(body.get("expiresIn").asLong()).isEqualTo(Duration.ofMinutes(15).toSeconds());
	}

	/**
	 * Rotation inherits the original expiry, so the reported remaining lifetime never climbs back to a
	 * full twelve hours. A session cannot be kept alive indefinitely by refreshing.
	 */
	@Test
	void reportsTheRemainingLifetimeOnRefreshRatherThanAFreshTwelveHours() throws Exception {
		saveAdmin("shaperotate@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse login = loginSuccessfully("shaperotate@tandf.example");

		long afterRefresh = bodyAsJson(callRefresh(login.refreshToken())).get("refreshExpiresIn").asLong();

		assertThat(afterRefresh).isPositive().isLessThanOrEqualTo(login.refreshExpiresIn());
		assertThat(this.onlySession().getExpiresAt())
				.isBefore(java.time.Instant.now().plus(Duration.ofHours(12)).plusSeconds(5));
	}

	@Test
	void meReturnsTheContractsAdminUserShape() throws Exception {
		AdminUser admin = saveAdmin("shapeme@tandf.example", AdminRole.INSTITUTION_ADMIN, AdminStatus.ACTIVE,
				null, "institution-7");
		AdminLoginResponse tokens = loginSuccessfully("shapeme@tandf.example");

		MvcResult result = callMe(tokens.accessToken());

		assertThat(result.getResponse().getStatus()).isEqualTo(200);
		assertThat(result.getResponse().getContentType()).startsWith("application/json");

		JsonNode body = bodyAsJson(result);
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("id", "email", "name", "role",
				"scopePublisherId", "scopeInstitutionId", "status");
		assertThat(body.get("id").asString()).isEqualTo(admin.getId());
		assertThat(body.get("scopeInstitutionId").asString()).isEqualTo("institution-7");
		assertThat(body.get("scopePublisherId").isNull()).isTrue();
	}

	/** Logout is 204 with an empty body, whatever the outcome. */
	@Test
	void logoutReturnsNoContentAndNoBody() throws Exception {
		saveAdmin("shapelogout@tandf.example", AdminRole.SUPER_ADMIN, AdminStatus.ACTIVE);
		AdminLoginResponse tokens = loginSuccessfully("shapelogout@tandf.example");

		MvcResult result = callLogout(tokens.refreshToken());

		assertThat(result.getResponse().getStatus()).isEqualTo(204);
		assertThat(result.getResponse().getContentAsString()).isEmpty();
		assertThat(result.getResponse().getContentType()).isNull();
	}

	private JsonNode bodyAsJson(MvcResult result) throws Exception {
		return this.objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
