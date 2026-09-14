package com.tf.reader.hold.service;

import java.time.Instant;

// PROPOSAL — nothing published can create a loan yet, and accept must.
// Local to hold until Shashank ships a real command; the same one the read
// broker needs. Swapping the implementation is one class.
public interface LoanProvisioning {

    LoanReceipt createFromAcceptedOffer(String userId, String itemId, String holdId);

    record LoanReceipt(String loanId, Instant dueAt) {
    }
}
