package com.tf.reader.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
		log.warn("Failed to read JSON request body: {}", exception.getMessage(), exception);
		String causeMessage = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
		return respond(ErrorCode.VALIDATION_FAILED, "A JSON request body is required: " + causeMessage);
	}

	/** Catch-all: any unhandled exception becomes a 500 with a traceId the team can grep for. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
		String traceId = TraceId.next();
		log.error("Unhandled exception, traceId={}", traceId, exception);
		String message = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
		return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred: " + message, traceId);
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message) {
		return respond(code, message, TraceId.next());
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message, String traceId) {
		return ResponseEntity.status(code.status()).body(ApiError.of(code, message, traceId));
	}
}
