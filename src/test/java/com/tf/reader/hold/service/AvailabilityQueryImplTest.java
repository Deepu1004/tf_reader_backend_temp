package com.tf.reader.hold.service;

import com.tf.reader.reading.api.CopyLease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// Two Redis reads, no Mongo query — if this ever needs Mongo, the design is
// wrong. Never throws for a data reason.
class AvailabilityQueryImplTest {

    private final CopyLease lease = mock(CopyLease.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zsetOps = mock(ZSetOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-17T09:00:00Z"), ZoneOffset.UTC);

    private AvailabilityQueryImpl availability;

    @BeforeEach
    void setUp() {
        when(redis.opsForZSet()).thenReturn(zsetOps);
        availability = new AvailabilityQueryImpl(lease, redis, clock);
    }

    @Test
    void nullCopiesOmitsTheNumbersRatherThanZeroingThem() {
        var result = availability.forItem("inst_1", "item_1", null);

        assertThat(result.available()).isNull();
        assertThat(result.queueLength()).isNull();
        assertThat(result.serverTime()).isEqualTo(clock.instant());
        verifyNoInteractions(lease);
    }

    @Test
    void computesAvailableFromCopiesMinusLeased() {
        when(lease.available("inst_1", "item_1", 2)).thenReturn(1);
        when(zsetOps.zCard(anyString())).thenReturn(3L);

        var result = availability.forItem("inst_1", "item_1", 2);

        assertThat(result.available()).isEqualTo(1);
        assertThat(result.queueLength()).isEqualTo(3);
    }

    @Test
    void neverGoesNegativeWhenLeasedExceedsCopies() {
        when(lease.available("inst_1", "item_1", 2)).thenReturn(0);
        when(zsetOps.zCard(anyString())).thenReturn(0L);

        var result = availability.forItem("inst_1", "item_1", 2);

        assertThat(result.available()).isEqualTo(0);
    }

    @Test
    void anExceptionAnywhereStillProducesAnHonestUnknownAnswer() {
        when(lease.available(anyString(), anyString(), anyInt())).thenThrow(new RuntimeException("Redis unreachable"));

        var result = availability.forItem("inst_1", "item_1", 2);

        assertThat(result.available()).isNull();
        assertThat(result.queueLength()).isNull();
    }
}
