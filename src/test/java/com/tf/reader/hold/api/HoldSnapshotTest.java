package com.tf.reader.hold.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HoldSnapshotTest {

    @Test
    void fromCopiesEveryFieldOffAHoldViewUnchanged() {
        var offer = new OfferView("offer_1", Instant.now(), Instant.now().plusSeconds(900));
        var view = new HoldView("hold_1", "item_1", "OFFERED", 0, 4, null, Instant.now(), offer);

        var snapshot = HoldSnapshot.from(view);

        assertThat(snapshot.holdId()).isEqualTo(view.holdId());
        assertThat(snapshot.itemId()).isEqualTo(view.itemId());
        assertThat(snapshot.status()).isEqualTo(view.status());
        assertThat(snapshot.queueLength()).isEqualTo(view.queueLength());
        assertThat(snapshot.offer()).isEqualTo(offer);
    }
}
