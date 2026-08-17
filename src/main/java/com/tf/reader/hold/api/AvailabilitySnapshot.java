package com.tf.reader.hold.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// Read model of copy availability for one title.
//
// available and queueLength are OMITTED, never zero, whenever the number is
// unknown or the title has no copy limit — a zero would render as "none
// free" and hide a real book behind a wrong number.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AvailabilitySnapshot(
        Integer available,
        Integer queueLength,
        Instant serverTime
) {
    public static AvailabilitySnapshot unknown(Instant now) {
        return new AvailabilitySnapshot(null, null, now);
    }
}
