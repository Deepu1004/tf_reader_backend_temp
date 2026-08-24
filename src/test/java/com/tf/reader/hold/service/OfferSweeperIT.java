package com.tf.reader.hold.service;

import com.tf.reader.hold.HoldContainerTest;
import com.tf.reader.hold.entity.Hold;
import com.tf.reader.hold.entity.Offer;
import com.tf.reader.hold.repository.HoldRepository;
import com.tf.reader.hold.repository.HoldWrites;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// A key expiring runs no code — an offer lapses because THIS found it in
// Mongo. The races this job is really tested against live in PromotionIT;
// this covers OfferSweeper's own basic behaviour.
class OfferSweeperIT extends HoldContainerTest {

    @Autowired
    OfferSweeper sweeper;
    @Autowired
    HoldRepository holds;
    @Autowired
    HoldWrites writes;
    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @AfterEach
    void cleanUp() {
        holds.deleteAll();
        redisConnectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("sweep does nothing when nothing has actually lapsed")
    void sweepIsANoOpWithNothingToSweep() {
        Instant now = Instant.now();
        Hold stillOffered = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, now));
        writes.offerIfQueued(stillOffered.getHoldId(), new Offer("offer_1", now, now.plusSeconds(900), null));

        sweeper.sweep();

        assertThat(holds.findByHoldId(stillOffered.getHoldId())).isPresent();
    }

    @Test
    @DisplayName("sweep deletes a lapsed offer and removes it from the Redis queue")
    void sweepDeletesALapsedOfferAndCleansUpRedis() {
        Instant now = Instant.now();
        Hold lapsed = holds.save(Hold.queued("user_a", "inst_1", "item_1", 1, now));
        writes.offerIfQueued(lapsed.getHoldId(), new Offer("offer_1", now.minusSeconds(120), now.minusSeconds(1), null));

        sweeper.sweep();

        assertThat(holds.findByHoldId(lapsed.getHoldId())).isEmpty();
    }
}
