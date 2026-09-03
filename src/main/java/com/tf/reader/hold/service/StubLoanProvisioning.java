package com.tf.reader.hold.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

// TEMPORARY. Remove once Shashank publishes a real loan-creation command —
// this exists only so accept() has something real to call and test today.
@Component
public class StubLoanProvisioning implements LoanProvisioning {

    private final Clock clock;

    public StubLoanProvisioning(Clock clock) {
        this.clock = clock;
    }

    @Override
    public LoanReceipt createFromAcceptedOffer(String userId, String itemId, String holdId) {
        return new LoanReceipt("loan_stub_" + holdId, clock.instant().plus(Duration.ofDays(21)));
    }
}
