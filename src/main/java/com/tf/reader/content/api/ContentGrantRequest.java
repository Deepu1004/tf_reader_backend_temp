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
        boolean wantSearchIndex,
        // Nullable, defaults to 1 - a non-chaptered item has exactly one part, so an existing
        // caller that never heard of chapters keeps getting exactly what it always got.
        Integer partNumber
) {
    // Kept so flambeau's existing 7-arg call site keeps compiling unchanged (this record is one
    // of the two Java seams into wokay, per .claude/context/shared.md) - defaults to part 1,
    // identical behaviour to today, until they pick this up on their own schedule.
    public ContentGrantRequest(String itemId, Format format, Intent intent, byte[] devicePublicKey,
            SubjectRef subject, LoanProof loanProof, boolean wantSearchIndex) {
        this(itemId, format, intent, devicePublicKey, subject, loanProof, wantSearchIndex, null);
    }
}
