package com.tf.reader.hold.service;

import com.tf.reader.hold.api.AvailabilityQuery;
import com.tf.reader.hold.api.AvailabilitySnapshot;
import com.tf.reader.reading.api.CopyLease;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

// Implementation of the AvailabilityQuery contract. Two Redis reads, no
// Mongo query — if this ever needs Mongo, the design is wrong and the 50ms
// budget is gone. Never throws for a data reason: a book page has to render
// regardless of what went wrong underneath.
@Service
public class AvailabilityQueryImpl implements AvailabilityQuery {

    private final CopyLease lease;
    private final StringRedisTemplate redis;
    private final Clock clock;

    public AvailabilityQueryImpl(CopyLease lease, StringRedisTemplate redis, Clock clock) {
        this.lease = lease;
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public AvailabilitySnapshot forItem(String scope, String itemId, Integer copies) {
        Instant now = clock.instant();
        try {
            if (copies == null) {
                // No copy limit, or entitlement couldn't be checked — the
                // honest answer is identical either way: omit, don't guess.
                return AvailabilitySnapshot.unknown(now);
            }
            int available = lease.available(scope, itemId, copies);
            Long queueLength = redis.opsForZSet().zCard(QueueKeys.queueKey(scope, itemId));
            return new AvailabilitySnapshot(available, queueLength == null ? 0 : queueLength.intValue(), null, now);
        } catch (RuntimeException e) {
            return AvailabilitySnapshot.unknown(now);
        }
    }
}

