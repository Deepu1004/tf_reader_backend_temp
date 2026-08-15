package com.tf.reader.common.error;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The one source of the {@code traceId} carried by every error response.
 *
 * <p>Shared by {@link GlobalExceptionHandler} and {@link ErrorResponseWriter} so a failure raised
 * inside a controller and one raised in the security filter chain produce ids of the same shape.
 *
 * <p>Not a secret and not a security control, so {@link ThreadLocalRandom} is the right tool: the id
 * only has to be unique enough to find one request in a log file.
 */
public final class TraceIds {

	private TraceIds() {
	}

	public static String newTraceId() {
		return Long.toHexString(ThreadLocalRandom.current().nextLong());
	}

}
