package com.tf.reader.common.error;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Builds the contract's {@link ApiError} envelope. Shared by the {@code @RestControllerAdvice} and the
 * filter-chain handlers, so a failure before routing looks identical to one inside a controller.
 */
public final class ApiErrors {

	/** One message for every 401, so a response cannot be used to probe token or account state. */
	public static final String UNAUTHENTICATED_MESSAGE = "Authentication is required.";

	/** Never names the role or scope that would have been required. */
	public static final String FORBIDDEN_MESSAGE = "You do not have permission to access this resource.";

	public static final String VALIDATION_FAILED_MESSAGE = "Request validation failed.";

	private ApiErrors() {
	}

	public static ApiError of(HttpStatusCode status, ErrorCode code, String message, String path) {
		return new ApiError(Instant.now().toString(), status.value(), code, message, path);
	}

	public static ApiError unauthenticated(String path) {
		return of(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, UNAUTHENTICATED_MESSAGE, path);
	}

	public static ApiError forbidden(String path) {
		return of(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN_SCOPE, FORBIDDEN_MESSAGE, path);
	}

	public static ApiError validationFailed(String path) {
		return of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_FAILED_MESSAGE, path);
	}

}
