package com.tf.reader.content.api;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.SubjectRef;


public record ContentGrantRequest(
        String itemId,
        Format format,
        Intent intent,
        byte[] devicePublicKey,
        SubjectRef subject,
        LoanProof loanProof,
        boolean wantSearchIndex
) {
}
