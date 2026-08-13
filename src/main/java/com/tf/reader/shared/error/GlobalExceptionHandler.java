package com.tf.reader.shared.error;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The single advice class for the whole backend. Nobody writes their own.
 *
 * <p>API Reference section 2: every refusal is {@code {code, message, traceId}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApiException(ApiException exception) {
		return respond(exception.code(), exception.getMessage());
	}

	/** A request body that failed bean validation, e.g. a missing institutionId. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.orElse("The request body is not valid.");
		return respond(ErrorCode.VALIDATION_FAILED, message);
	}

	/** A body that is absent or is not parseable JSON. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
		return respond(ErrorCode.VALIDATION_FAILED, "A JSON request body is required.");
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message) {
		ApiError body = ApiError.of(code, message, TraceId.next());
		return ResponseEntity.status(code.status()).body(body);
	}
}
