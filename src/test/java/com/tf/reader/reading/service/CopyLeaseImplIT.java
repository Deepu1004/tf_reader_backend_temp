package com.tf.reader.reading.service;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Real Redis, not a mock — a Lua script is only correct if EVAL actually runs it, and
// a mocked StringRedisTemplate would let a script full of typos pass every test.
@SpringBootTest
class CopyLeaseImplIT extends ContainerisedInfrastructure {

	private static final String SCOPE = "inst_1";
	private static final String ITEM = "item_1";

	@Autowired
	CopyLease lease;
	@Autowired
	RedisConnectionFactory redisConnectionFactory;

	@AfterEach
	void cleanUp() {
		redisConnectionFactory.getConnection().serverCommands().flushAll();
	}

	@Test
	void claimSucceedsUnderTheLimitAndFailsOverIt() {
		Optional<LeaseHandle> first = lease.claim(SCOPE, ITEM, 1);
		Optional<LeaseHandle> second = lease.claim(SCOPE, ITEM, 1);

		assertThat(first).isPresent();
		assertThat(second).isEmpty();
	}

	@Test
	void releaseByTokenFreesTheSlotWithNoScopeOrItemIdSupplied() {
		LeaseHandle held = lease.claim(SCOPE, ITEM, 1).orElseThrow();

		lease.release(held.token());

		assertThat(lease.claim(SCOPE, ITEM, 1)).isPresent();
	}

	@Test
	void availableReflectsActiveClaimsOnly() {
		lease.claim(SCOPE, ITEM, 2);

		assertThat(lease.available(SCOPE, ITEM, 2)).isEqualTo(1);
	}

	@Test
	void extendFailsForATokenThatNoLongerHoldsTheSlot() {
		LeaseHandle held = lease.claim(SCOPE, ITEM, 1).orElseThrow();
		lease.release(held.token());

		assertThat(lease.extend(held, Instant.now().plusSeconds(60))).isFalse();
	}

	@Test
	void reassignMovesTheSlotToANewTokenWithoutFreeingIt() {
		LeaseHandle held = lease.claim(SCOPE, ITEM, 1).orElseThrow();

		lease.reassign(SCOPE, ITEM, held.token(), "lease_new1", Instant.now().plusSeconds(60));

		assertThat(lease.claim(SCOPE, ITEM, 1)).isEmpty(); // slot still taken, by the new token
		lease.release("lease_new1");
		assertThat(lease.claim(SCOPE, ITEM, 1)).isPresent(); // releasing the new token frees it
	}
}
