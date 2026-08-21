package com.tf.reader.hold.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// Read model of a single reader's hold. The one shape QueueService computes
// — position and queueLength are read live off Redis, never stored.
//
// This is deliberately a SEPARATE type from api/HoldSnapshot, even though
// today the fields are identical: this is what the HTTP controller wraps
// into dto/HoldResponse, HoldSnapshot is what the published
// HoldSnapshotQuery hands to another module. If the HTTP response ever needs
// to diverge from what library reads, they don't have to move in lockstep.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HoldView(
        String holdId,
        String itemId,
        String status,
        int position,
        int queueLength,
        Integer estimatedWaitDays,   // a guess, never a promise — omitted once OFFERED, there's a real deadline instead
        Instant placedAt,
        OfferView offer
) {
}
