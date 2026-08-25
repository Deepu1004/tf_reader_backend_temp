package com.tf.reader.hold.repository;

import com.tf.reader.hold.HoldContainerTest;
import com.tf.reader.hold.entity.Hold;
import com.tf.reader.hold.entity.HoldStatus;
import com.tf.reader.hold.entity.Offer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Every one of these is "status in the filter" made visible: the guard
// isn't application code checking a status then writing — it's one Mongo
// operation whose filter already includes the status.
class HoldWritesIT extends HoldContainerTest {

    @Autowired
    HoldRepository holds;
    @Autowired
    HoldWrites writes;

    @AfterEach
    void cleanUp() {
        holds.deleteAll();
    }

    @Test
    @DisplayName("offerIfQueued moves QUEUED to OFFERED and returns the updated document")
    void offerIfQueuedTransitions() {
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, Instant.now()));
        Offer offer = new Offer("offer_1", Instant.now(), Instant.now().plusSeconds(900), null);

        var updated = writes.offerIfQueued(hold.getHoldId(), offer);

        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(HoldStatus.OFFERED);
        assertThat(updated.get().getOffer().getOfferId()).isEqualTo("offer_1");
    }

    @Test
    @DisplayName("offerIfQueued matches nothing once the hold is already OFFERED")
    void offerIfQueuedIsNotReentrant() {
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, Instant.now()));
        writes.offerIfQueued(hold.getHoldId(), new Offer("offer_1", Instant.now(), Instant.now().plusSeconds(900), null));

        var second = writes.offerIfQueued(hold.getHoldId(), new Offer("offer_2", Instant.now(), Instant.now().plusSeconds(900), null));

        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("claimIfLive wins only while OFFERED and the deadline is still in the future")
    void claimIfLiveHonoursTheDeadline() {
        Instant now = Instant.now();
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, now));
        writes.offerIfQueued(hold.getHoldId(), new Offer("offer_1", now, now.plusSeconds(900), null));

        var claimed = writes.claimIfLive(hold.getHoldId(), "user_a", now);

        assertThat(claimed).isPresent();
        assertThat(holds.findByHoldId(hold.getHoldId())).as("winning deletes the hold in the same step").isEmpty();
    }

    @Test
    @DisplayName("claimIfLive refuses a deadline that has already passed")
    void claimIfLiveRefusesAfterTheDeadline() {
        Instant now = Instant.now();
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, now));
        writes.offerIfQueued(hold.getHoldId(), new Offer("offer_1", now.minusSeconds(120), now.minusSeconds(60), null));

        var claimed = writes.claimIfLive(hold.getHoldId(), "user_a", now);

        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("expireIfLapsed is the sweep's half of the same race — it only wins after the deadline")
    void expireIfLapsedHonoursTheDeadline() {
        Instant now = Instant.now();
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, now));
        writes.offerIfQueued(hold.getHoldId(), new Offer("offer_1", now.minusSeconds(120), now.minusSeconds(60), null));

        var expired = writes.expireIfLapsed(hold.getHoldId(), now);

        assertThat(expired).isPresent();
        assertThat(holds.findByHoldId(hold.getHoldId())).isEmpty();
    }

    @Test
    @DisplayName("deleteOwn is the contract's only genuine hard delete, and is safe to call twice")
    void deleteOwnIsIdempotent() {
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, Instant.now()));

        assertThat(writes.deleteOwn(hold.getHoldId(), "user_a")).isPresent();
        assertThat(writes.deleteOwn(hold.getHoldId(), "user_a")).as("second call touches nothing").isEmpty();
        assertThat(holds.findByHoldId(hold.getHoldId())).isEmpty();
    }

    @Test
    @DisplayName("deleteOwn never lets a caller remove somebody else's hold")
    void deleteOwnIsScopedToTheCaller() {
        Hold hold = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, Instant.now()));

        assertThat(writes.deleteOwn(hold.getHoldId(), "user_stranger")).isEmpty();
        assertThat(holds.findByHoldId(hold.getHoldId())).isPresent();
    }
}
