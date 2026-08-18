package com.tf.reader.shared.error;

/**
 * The one error body the whole backend returns. Fixed shape, API Reference section 2.
 *
 * <p>{@code code} is stable and machine-readable; {@code message} is for humans and may
 * change without notice. An upstream error body is never copied into either field.
 */
public record ApiError(String code, String message, String traceId) {

	public static ApiError of(ErrorCode code, String message, String traceId) {
		return new ApiError(code.name(), message, traceId);
	}
}
