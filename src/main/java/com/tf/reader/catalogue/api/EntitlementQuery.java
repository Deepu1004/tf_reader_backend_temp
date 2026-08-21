package com.tf.reader.catalogue.api;

public interface EntitlementQuery {
    EntitlementDecision check(SubjectRef subject, String itemId);
}
