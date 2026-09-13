package com.tf.reader.hold.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// TEMPORARY, and so is this test — delete both once Shashank publishes a
// real loan-creation command and accept() calls that instead.
class StubLoanProvisioningTest {

    @Test
    void returnsAReceiptSoAcceptHasSomethingRealToHandBack() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T09:00:00Z"), ZoneOffset.UTC);
        var provisioning = new StubLoanProvisioning(clock);

        var receipt = provisioning.createFromAcceptedOffer("user_a", "item_1", "hold_1");

        assertThat(receipt.loanId()).contains("hold_1");
        assertThat(receipt.dueAt()).isAfter(clock.instant());
    }
}
