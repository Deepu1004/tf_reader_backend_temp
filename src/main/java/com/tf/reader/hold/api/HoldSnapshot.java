package com.tf.reader.hold.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

// Read model handed to another module through HoldSnapshotQuery. Same
// fields as HoldView by design today — see HoldView's note on why they're
// still two separate types.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HoldSnapshot(
        String holdId,
        String itemId,
        String status,
        int position,
        int queueLength,
        Integer estimatedWaitDays,
        Instant placedAt,
        OfferView offer
) {
    public static HoldSnapshot from(HoldView v) {
        return new HoldSnapshot(v.holdId(), v.itemId(), v.status(), v.position(),
                v.queueLength(), v.estimatedWaitDays(), v.placedAt(), v.offer());
    }
}
