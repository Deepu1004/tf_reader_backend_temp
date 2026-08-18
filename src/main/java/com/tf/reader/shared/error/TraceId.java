package com.tf.reader.shared.error;

import java.util.UUID;

/**
 * The correlation id every error body carries, so a user reporting "it said something went
 * wrong" can be matched to a log line.
 */
public final class TraceId {

	private TraceId() {
	}

	public static String next() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}
}
