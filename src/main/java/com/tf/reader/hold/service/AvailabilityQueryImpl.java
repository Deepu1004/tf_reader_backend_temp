package com.tf.reader.hold.service;

import com.tf.reader.hold.api.AvailabilityQuery;
import com.tf.reader.hold.api.AvailabilitySnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Implementation of the AvailabilityQuery contract.
 */
@Service
public class AvailabilityQueryImpl implements AvailabilityQuery {

    @Override
    public AvailabilitySnapshot forItem(String scope, String itemId, Integer copies) {
        if (copies == null) {
            return AvailabilitySnapshot.unknown(Instant.now());
        }
        return new AvailabilitySnapshot(copies, 0, null, Instant.now());
    }
}

