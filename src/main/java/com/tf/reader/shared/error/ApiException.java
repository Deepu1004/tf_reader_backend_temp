package com.tf.reader.shared.error;

/**
 * A refusal that carries its own error code. Services throw this; the advice class turns
 * it into the one canonical error body. No service builds a ResponseEntity itself.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	public ApiException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}
}
