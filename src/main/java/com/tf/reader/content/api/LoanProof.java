package com.tf.reader.content.api;

import java.time.Instant;

public record LoanProof(
        String loanId,
        Instant dueAt
) {
}
