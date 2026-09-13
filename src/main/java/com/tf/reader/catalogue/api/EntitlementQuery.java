package com.tf.reader.catalogue.api;

import java.util.List;
import java.util.Map;

public interface EntitlementQuery {
    EntitlementDecision check(SubjectRef subject, String itemId);

    /**
     * Same decision as {@link #check}, for every id in one call - the institution and publisher
     * lookups happen once for the whole batch rather than once per item.
     */
    Map<String, EntitlementDecision> checkAll(SubjectRef subject, List<String> itemIds);
}
