package com.tf.reader.catalogue.service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import org.springframework.stereotype.Service;

@Service
class EntitlementQueryImpl implements EntitlementQuery {

    private static final String PLACEHOLDER_ENTITLEMENT_ID = "ent_placeholder";
    private static final int PLACEHOLDER_COPIES = 2;
    private static final int PLACEHOLDER_LOAN_PERIOD_DAYS = 14;

    @Override
    public EntitlementDecision check(SubjectRef subject, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }

        return new EntitlementDecision(
                true,
                AccessLevel.ENTITLED_CONCURRENT,
                PLACEHOLDER_ENTITLEMENT_ID,
                PLACEHOLDER_COPIES,
                PLACEHOLDER_LOAN_PERIOD_DAYS,
                null,
                null
        );
    }
}
