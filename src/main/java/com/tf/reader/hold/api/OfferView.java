package com.tf.reader.hold.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// Read model of an offer made to a promoted reader. Embedded inside
// HoldView / HoldSnapshot — null there unless a hold is actually OFFERED.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferView(
        String offerId,
        Instant offeredAt,
        Instant expiresAt   // ABSOLUTE — the app renders a countdown against it, never a duration
) {
}
