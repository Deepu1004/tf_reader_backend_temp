package com.tf.reader.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsAnApiExceptionToTheStandardShape() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/v1/content/item_42/access-url");

		ResponseEntity<ErrorResponse> response = handler.handleApiException(
				new ApiException(ErrorCode.NO_ENTITLEMENT, "Your institution is not entitled to this title."),
				request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody().code()).isEqualTo("NO_ENTITLEMENT");
		assertThat(response.getBody().message()).isEqualTo("Your institution is not entitled to this title.");
		assertThat(response.getBody().path()).isEqualTo("/api/v1/content/item_42/access-url");
		assertThat(response.getBody().traceId()).isNotBlank();
		assertThat(response.getBody().timestamp()).isNotNull();
	}

	@Test
	void mapsAValidationFailureToTheStandardShape() throws NoSuchMethodException {
		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
		MethodParameter methodParameter = new MethodParameter(
				GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget"), -1);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

		ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void mapsAMalformedRequestBodyToValidationFailed() {
		ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(
				new HttpMessageNotReadableException("JSON parse error", (HttpInputMessage) null),
				new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void mapsAnUnexpectedExceptionToTheStandardShapeWithoutLeakingItsMessage() {
		ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
				new IllegalStateException("connection pool exhausted"), new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().message()).doesNotContain("connection pool exhausted");
	}

	@SuppressWarnings("unused")
	private void dummyTarget() {
	}

}
