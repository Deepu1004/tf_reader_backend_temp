package com.tf.reader.hold.service;

import com.tf.reader.hold.HoldContainerTest;
import com.tf.reader.hold.entity.Hold;
import com.tf.reader.hold.repository.HoldRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Real Mongo and real Redis (Testcontainers), not mocks — the point of the reconciler is that
// it re-derives Redis from what Mongo actually holds, so both sides have to be real for a
// green test to mean anything.
class QueueReconcilerIT extends HoldContainerTest {

    private static final String SCOPE = "inst_recon_q";
    private static final String ITEM = "item_recon_q";

    @Autowired
    QueueReconciler reconciler;
    @Autowired
    HoldRepository holds;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @AfterEach
    void cleanUp() {
        redisConnectionFactory.getConnection().serverCommands().flushAll();
        holds.deleteAll();
    }

    @Test
    void redisWipeMidFlightRestoresExactQueueOrder() {
        // Tickets deliberately out of save order and non-sequential, so a passing test can't be
        // an accident of insertion order.
        holds.save(Hold.queued("user_c", SCOPE, ITEM, 30L, Instant.now()));
        holds.save(Hold.queued("user_a", SCOPE, ITEM, 10L, Instant.now()));
        holds.save(Hold.queued("user_b", SCOPE, ITEM, 20L, Instant.now()));

        redisConnectionFactory.getConnection().serverCommands().flushAll();

        reconciler.reconcile();

        String queueKey = QueueKeys.queueKey(SCOPE, ITEM);
        assertThat(redis.opsForZSet().rank(queueKey, QueueKeys.member("user_a"))).isEqualTo(0L);
        assertThat(redis.opsForZSet().rank(queueKey, QueueKeys.member("user_b"))).isEqualTo(1L);
        assertThat(redis.opsForZSet().rank(queueKey, QueueKeys.member("user_c"))).isEqualTo(2L);
    }

    @Test
    void reconcileDropsAGenuinelyStaleEntryNoLongerBackedByAnyQueuedHold() {
        // No Hold document at all for this item — the only way such an entry could exist for
        // real is a hold that left the queue (cancelled, promoted, expired) without its Redis
        // side ever being cleaned up. Discovered via the Redis-side KEYS scan, since Mongo has
        // nothing QUEUED for this item to point the reconciler at it otherwise.
        String queueKey = QueueKeys.queueKey(SCOPE, ITEM);
        redis.opsForZSet().add(queueKey, QueueKeys.member("user_ghost"), 1);

        reconciler.reconcile();

        assertThat(redis.opsForZSet().rank(queueKey, QueueKeys.member("user_ghost"))).isNull();
    }

    @Test
    void reconcileBumpsTheTicketCounterToTheHighestTicketMongoActuallyHandedOut() {
        // Simulates a Redis flush that lost the ticket counter but not Mongo's own record of
        // what ticket it already issued — without the bump, the next join() would INCR from
        // zero and hand out ticket 1, colliding with this hold's ticket 7.
        holds.save(Hold.queued("user_a", SCOPE, ITEM, 7L, Instant.now()));

        reconciler.reconcile();

        Long next = redis.opsForValue().increment(QueueKeys.ticketKey(SCOPE, ITEM));
        assertThat(next).isEqualTo(8L);
    }
}
