package com.tf.reader.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

	/**
	 * Bridge for code in modules that use {@code common.error.ApiException} per STYLE.md.
	 * Translates to the same wire format so the client sees one consistent error shape.
	 */
	@ExceptionHandler(com.tf.reader.common.error.ApiException.class)
	public ResponseEntity<ApiError> handleCommonApiException(com.tf.reader.common.error.ApiException exception) {
		com.tf.reader.common.error.ErrorCode commonCode = exception.getCode();
		String traceId = TraceId.next();
		ApiError body = new ApiError(commonCode.name(), exception.getMessage(), traceId);
		return ResponseEntity.status(HttpStatus.valueOf(commonCode.getStatus().value())).body(body);
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

	/** Catch-all: any unhandled exception becomes a 500 with a traceId the team can grep for. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
		String traceId = TraceId.next();
		log.error("Unhandled exception, traceId={}", traceId, exception);
		return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", traceId);
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message) {
		return respond(code, message, TraceId.next());
	}

	private ResponseEntity<ApiError> respond(ErrorCode code, String message, String traceId) {
		return ResponseEntity.status(code.status()).body(ApiError.of(code, message, traceId));
	}
}
