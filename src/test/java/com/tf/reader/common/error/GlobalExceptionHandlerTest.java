package com.tf.reader.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;

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
	void mapsAValidationFailureToACleanFieldMessage() throws NoSuchMethodException {
		BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
		bindingResult.addError(new FieldError("target", "size", "must be between 1 and 100"));
		MethodParameter methodParameter = new MethodParameter(
				GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyTarget"), -1);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

		ResponseEntity<Object> response = handler.handleExceptionInternal(ex, null, new HttpHeaders(),
				HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(body.message()).isEqualTo("size must be between 1 and 100");
	}

	@Test
	void mapsAMalformedRequestBodyToValidationFailed() {
		HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
				"JSON parse error: unexpected token", (HttpInputMessage) null);

		ResponseEntity<Object> response = handler.handleExceptionInternal(ex, null, new HttpHeaders(),
				HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(body.message()).isEqualTo("Request body is malformed.");
		assertThat(body.message()).doesNotContain("JSON parse error");
	}

	@Test
	void preservesA404StatusRaisedByTheFramework() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/unknown");

		ResponseEntity<Object> response = handler.handleExceptionInternal(
				new IllegalStateException("no handler"), null, new HttpHeaders(), HttpStatus.NOT_FOUND,
				new ServletWebRequest(request));

		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(body.code()).isEqualTo("NOT_FOUND");
	}

	@Test
	void preservesA405StatusRaisedByTheFrameworkInsteadOfBecoming500() {
		ResponseEntity<Object> response = handler.handleExceptionInternal(
				new IllegalStateException("method not supported"), null, new HttpHeaders(),
				HttpStatus.METHOD_NOT_ALLOWED, new ServletWebRequest(new MockHttpServletRequest()));

		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
		// The code maps to 400 on its own; the body must still report the 405 actually sent.
		assertThat(body.status()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
	}

	@Test
	void answersAMethodSecurityDenialWithForbiddenRatherThanAServerError() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/admin/v1/publishers/pub_rtlg");

		ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
				new AccessDeniedException("Access Denied"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody().code()).isEqualTo("FORBIDDEN_SCOPE");
		assertThat(response.getBody().status()).isEqualTo(HttpStatus.FORBIDDEN.value());
		assertThat(response.getBody().path()).isEqualTo("/api/admin/v1/publishers/pub_rtlg");
		assertThat(response.getBody().traceId()).isNotBlank();
	}

	@Test
	void neverNamesTheMissingScopeInAForbiddenResponse() {
		ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
				new AccessDeniedException("Access is denied for publisher pub_secret"), new MockHttpServletRequest());

		assertThat(response.getBody().message()).doesNotContain("pub_secret");
	}

	@Test
	void reportsTheSameStatusInTheBodyAsInTheResponseOnEveryPath() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		assertBodyStatusMatches(handler.handleApiException(
				new ApiException(ErrorCode.STALE_VERSION, "Stale."), request));
		assertBodyStatusMatches(handler.handleAccessDenied(new AccessDeniedException("nope"), request));
		assertBodyStatusMatches(handler.handleUnexpected(new IllegalStateException("boom"), request));
	}

	private static void assertBodyStatusMatches(ResponseEntity<ErrorResponse> response) {
		assertThat(response.getBody().status()).isEqualTo(response.getStatusCode().value());
	}

	@Test
	void mapsARawConstraintViolationToACleanValidationFailedMessage() {
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		Set<ConstraintViolation<SizeHolder>> violations = validator.validate(new SizeHolder(0));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/v1/things");

		ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(
				new ConstraintViolationException(violations), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().message()).contains("size").contains("must be greater than or equal to 1");
	}

	@Test
	void mapsAnUnexpectedExceptionToTheStandardShapeWithoutLeakingItsMessage() {
		ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
				new IllegalStateException("connection pool exhausted"), new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
		assertThat(response.getBody().message()).doesNotContain("connection pool exhausted");
	}

	@Test
	void identifiesAHighlightSpanCollisionSoTheClientDoesNotRetryItsOwnId() {
		DuplicateKeyException ex = new DuplicateKeyException(
				"E11000 duplicate key error collection: reader.highlights index: highlight_span_uk dup key: { }");

		ResponseEntity<ErrorResponse> response = handler.handleDuplicateKey(ex, new MockHttpServletRequest());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().code()).isEqualTo("CODE_TAKEN");
		assertThat(response.getBody().message()).isEqualTo("HIGHLIGHT_LOCATOR_DUPLICATION");
	}

	@Test
	void identifiesABookmarkLocatorCollisionSoTheClientDoesNotRetryItsOwnId() {
		DuplicateKeyException ex = new DuplicateKeyException(
				"E11000 duplicate key error collection: reader.bookmarks index: bookmark_locator_uk dup key: { }");

		ResponseEntity<ErrorResponse> response = handler.handleDuplicateKey(ex, new MockHttpServletRequest());

		assertThat(response.getBody().message()).isEqualTo("BOOKMARK_LOCATOR_DUPLICATION");
	}

	@Test
	void fallsBackToTheGenericMessageForAnUnrecognisedUniqueIndex() {
		DuplicateKeyException ex = new DuplicateKeyException(
				"E11000 duplicate key error collection: reader.progress index: progress_user_book_uk dup key: { }");

		ResponseEntity<ErrorResponse> response = handler.handleDuplicateKey(ex, new MockHttpServletRequest());

		assertThat(response.getBody().message()).isEqualTo("A record already exists for this scope.");
	}

	@Test
	void mapsAPayloadTooLargeExceptionTo413WithValidationFailed() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRequestURI("/api/admin/v1/catalogue-items/item_42/content");

		ResponseEntity<ErrorResponse> response = handler.handlePayloadTooLarge(
				new PayloadTooLargeException("A file that will be locked may not exceed 20 MB"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getBody().status()).isEqualTo(413);
		assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.getBody().message()).isEqualTo("A file that will be locked may not exceed 20 MB");
	}

	/**
	 * {@code MaxUploadSizeExceededException} has no explicit handler here on purpose - the base
	 * {@code ResponseEntityExceptionHandler} already claims it, so a second declaration is an
	 * "ambiguous @ExceptionHandler" startup failure, not a silent override. It still reaches
	 * {@code handleExceptionInternal} through Spring's own resolution, preserving whatever status
	 * that assigns via the existing 4xx fallback below - proven generically by
	 * {@link #preservesA405StatusRaisedByTheFrameworkInsteadOfBecoming500()} for the same fallback
	 * branch, since {@code MaxUploadSizeExceededException} itself needs a real Spring MVC dispatch
	 * to reach a real status rather than the framework-default 500 a bare unit test would see.
	 */
	@Test
	void preservesA413StatusRaisedByTheFrameworkForAnUploadThatIsTooLarge() {
		ResponseEntity<Object> response = handler.handleExceptionInternal(
				new MaxUploadSizeExceededException(115_343_360L), null, new HttpHeaders(),
				HttpStatus.CONTENT_TOO_LARGE, new ServletWebRequest(new MockHttpServletRequest()));

		ErrorResponse body = (ErrorResponse) response.getBody();
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(body.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(body.status()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
	}

	@SuppressWarnings("unused")
	private void dummyTarget() {
	}

	private record SizeHolder(@Min(1) int size) {
	}

}
