package com.tf.reader.hold.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Read model of copy availability for a single title.
 *
 * Fields are nullable when the value is unknown; callers must treat values
 * as best-effort and handle missing numbers accordingly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvailabilitySnapshot(
        Integer available,
        Integer queueLength,
        Integer myPosition,
        Instant serverTime
) {
    public static AvailabilitySnapshot unknown(Instant now) {
        return new AvailabilitySnapshot(null, null, null, now);
    }
}
