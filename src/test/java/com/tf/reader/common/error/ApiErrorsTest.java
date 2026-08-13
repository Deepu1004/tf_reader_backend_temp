package com.tf.reader.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * The envelope factory shared by the controller advice and the filter-chain handlers.
 *
 * <p>Covers the 403 shape directly, because no admin endpoint currently denies an authenticated admin
 * and there is therefore no HTTP path that produces one to assert against.
 */
class ApiErrorsTest {

	@Test
	void buildsTheUnauthenticatedEnvelope() {
		ApiError error = ApiErrors.unauthenticated("/api/admin/v1/auth/me");

		assertThat(error.status()).isEqualTo(401);
		assertThat(error.code()).isEqualTo(ErrorCode.UNAUTHENTICATED);
		assertThat(error.message()).isEqualTo(ApiErrors.UNAUTHENTICATED_MESSAGE);
		assertThat(error.path()).isEqualTo("/api/admin/v1/auth/me");
		assertThat(Instant.parse(error.timestamp())).isNotNull();
	}

	@Test
	void buildsTheForbiddenEnvelope() {
		ApiError error = ApiErrors.forbidden("/api/admin/v1/catalogue-items/item_99");

		assertThat(error.status()).isEqualTo(403);
		assertThat(error.code()).isEqualTo(ErrorCode.FORBIDDEN_SCOPE);
		assertThat(error.path()).isEqualTo("/api/admin/v1/catalogue-items/item_99");
	}

	@Test
	void buildsTheValidationFailedEnvelope() {
		ApiError error = ApiErrors.validationFailed("/api/admin/v1/auth/login");

		assertThat(error.status()).isEqualTo(400);
		assertThat(error.code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	/** A denial must never name the role or scope that would have been required. */
	@Test
	void neverNamesTheRequiredScopeInADenial() {
		assertThat(ApiErrors.forbidden("/api/admin/v1/publishers").message())
				.doesNotContainIgnoringCase("publisher", "role", "scope", "admin");
	}

}
