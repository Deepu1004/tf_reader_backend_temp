package com.tf.reader.catalogue.api;

import java.time.Instant;

public record EntitlementDecision(
        boolean entitled,
        AccessLevel accessLevel,
        String entitlementId,
        Integer copies,
        int loanPeriodDays,
        Instant validTo,
        DenyReason reason
) {
}
