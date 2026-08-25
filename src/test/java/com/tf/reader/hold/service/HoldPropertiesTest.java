package com.tf.reader.hold.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

// Correctness, not taste — a test, not a comment.
class HoldPropertiesTest {

    @Test
    void defaultsPassValidation() {
        HoldProperties props = new HoldProperties();
        assertThatCode(props::validate).doesNotThrowAnyException();
    }

    @Test
    void refusesLeaseSlackShorterThanTwiceTheSweepInterval() {
        HoldProperties props = new HoldProperties();
        props.setSweepInterval(Duration.ofSeconds(10));
        props.setLeaseSlack(Duration.ofSeconds(15)); // needs to be >= 20s

        assertThatIllegalStateException().isThrownBy(props::validate);
    }

    @Test
    void refusesAPromoteLockTtlLongEnoughToFreezeAQueue() {
        HoldProperties props = new HoldProperties();
        props.setPromoteLockTtl(Duration.ofMinutes(2));

        assertThatIllegalStateException().isThrownBy(props::validate);
    }
}
