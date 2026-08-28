package com.tf.reader.ingest.api;

import java.time.Instant;

/** A time-limited URL for one stored object, and the instant it stops working. */
public record PresignedObject(String url, Instant expiresAt) {
}
