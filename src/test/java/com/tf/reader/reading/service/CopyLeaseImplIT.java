package com.tf.reader.reading.service;

import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CopyLeaseImplIT {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"));
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    CopyLease lease;
    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @AfterEach
    void cleanUp() {
        redisConnectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("claim succeeds up to the total, then refuses")
    void claimRespectsTheTotal() {
        assertThat(lease.claim("inst_1", "item_1", 2)).isPresent();
        assertThat(lease.claim("inst_1", "item_1", 2)).isPresent();
        assertThat(lease.claim("inst_1", "item_1", 2))
                .as("both copies are already out")
                .isEmpty();

        assertThat(lease.available("inst_1", "item_1", 2)).isEqualTo(0);
    }

    @Test
    @DisplayName("available never counts a row whose time is already up")
    void availableExcludesExpiredRows() {
        LeaseHandle held = lease.claim("inst_1", "item_1", 5).orElseThrow();

        assertThat(lease.extend(held, Instant.now().minusSeconds(1))).isTrue();

        assertThat(lease.available("inst_1", "item_1", 5))
                .as("ZCOUNT now +inf must not count a row whose time is already up")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("reassign hands the lease to somebody else, add before remove")
    void reassignMovesTheLease() {
        Instant until = Instant.now().plusSeconds(900);
        LeaseHandle held = lease.claim("inst_1", "item_1", 1).orElseThrow();

        lease.reassign("inst_1", "item_1", held.token(), "lease_new", until.plusSeconds(60));

        assertThat(lease.available("inst_1", "item_1", 1))
                .as("still exactly one row leased, just a different holder")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("release gives the copy back")
    void releaseFreesTheCopy() {
        LeaseHandle held = lease.claim("inst_1", "item_1", 1).orElseThrow();

        lease.release(held);

        assertThat(lease.available("inst_1", "item_1", 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("release by bare token also frees the copy")
    void releaseByTokenFreesTheCopy() {
        LeaseHandle held = lease.claim("inst_1", "item_1", 1).orElseThrow();

        lease.release(held.token());

        assertThat(lease.available("inst_1", "item_1", 1)).isEqualTo(1);
    }
}
