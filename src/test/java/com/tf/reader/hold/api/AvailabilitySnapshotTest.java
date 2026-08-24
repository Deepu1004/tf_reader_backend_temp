package com.tf.reader.hold.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilitySnapshotTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void unknownOmitsBothNumbersRatherThanZeroingThem() throws Exception {
        var snapshot = AvailabilitySnapshot.unknown(Instant.parse("2026-08-17T09:00:00Z"));

        assertThat(snapshot.available()).isNull();
        assertThat(snapshot.queueLength()).isNull();

        String json = mapper.writeValueAsString(snapshot);
        assertThat(json).doesNotContain("available").doesNotContain("queueLength");
        assertThat(json).contains("serverTime");
    }
}
