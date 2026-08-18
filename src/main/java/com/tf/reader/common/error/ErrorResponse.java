package com.tf.reader.common.error;

import java.time.Instant;

public record ErrorResponse(
		Instant timestamp,
		int status,
		String code,
		String message,
		String path,
		String traceId) {


	public static ErrorResponse of(ErrorCode code, String message, String path, String traceId) {
		return of(code.getStatus().value(), code, message, path, traceId);
	}

	/**
	 * Uses an explicit status, for the cases where the framework has already chosen one that differs
	 * from the code's own: a 405 or a 415 still carries {@code VALIDATION_FAILED}. The {@code status}
	 * in the body must always equal the HTTP status actually sent, or a client that reads the field
	 * and a client that reads the header disagree about what happened.
	 */
	public static ErrorResponse of(int status, ErrorCode code, String message, String path, String traceId) {
		return new ErrorResponse(Instant.now(), status, code.name(), message, path, traceId);
	}

}
