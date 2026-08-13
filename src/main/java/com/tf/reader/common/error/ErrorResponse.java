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
		return new ErrorResponse(Instant.now(), code.getStatus().value(), code.name(), message, path, traceId);
	}

}
