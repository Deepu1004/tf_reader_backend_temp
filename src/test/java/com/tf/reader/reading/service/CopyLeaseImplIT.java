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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

	@Test
	void acquireClaimsOneScopelessSlotAndBlocksASecondCall() {
		// acquire() is on the published API surface — Khushi's hold module may call it.
		// Verify it actually claims a slot against real Redis.
		Optional<LeaseHandle> first = lease.acquire(ITEM);
		Optional<LeaseHandle> second = lease.acquire(ITEM);

		assertThat(first).isPresent();
		assertThat(second).isEmpty(); // only 1 copy allowed via acquire()
	}

	@Test
	void acquiredSlotIsReleasedByTokenWithNoScopeOrItemIdSupplied() {
		LeaseHandle held = lease.acquire(ITEM).orElseThrow();

		lease.release(held.token());

		assertThat(lease.acquire(ITEM)).isPresent(); // slot is free again
	}

	@Test
	void acquiredSlotCanBeExtendedAndItsKeyIsConsistentWithNullScope() {
		// This test would fail if itemKey(null, itemId) produced "lease:null:itemId"
		// (the old code) instead of "lease::itemId" — extend() builds the key from
		// handle.scope() and handle.itemId(), and if that key differed from the one
		// claim() wrote into, ZSCORE would find nothing and return false.
		LeaseHandle held = lease.acquire(ITEM).orElseThrow();

		boolean extended = lease.extend(held, Instant.now().plusSeconds(120));

		assertThat(extended).isTrue(); // key round-trip was consistent
		assertThat(held.scope()).isNull(); // acquire() explicitly carries no scope
	}

	@Test
	void threeClaimantsRacingForOneSlotAdmitExactlyOne() throws Exception {
		// The race the Lua CLAIM script exists to close atomically: two (or more) threads both
		// see room under the copy limit and both proceed. A read-then-write implementation
		// would let all three succeed here. Run 20 times to catch any flakiness in the script.
		for (int run = 0; run < 20; run++) {
			redisConnectionFactory.getConnection().serverCommands().flushAll();

			CountDownLatch start = new CountDownLatch(1);
			ExecutorService pool = Executors.newFixedThreadPool(3);
			List<Future<Optional<LeaseHandle>>> futures = new ArrayList<>();

			for (int i = 0; i < 3; i++) {
				futures.add(pool.submit(() -> {
					start.await();
					return lease.claim(SCOPE, ITEM, 1);
				}));
			}
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

			long admitted = futures.stream()
					.map(CopyLeaseImplIT::get)
					.filter(Optional::isPresent)
					.count();

			assertThat(admitted)
					.as("run %d: exactly 1 of 3 concurrent claim() calls should win the single slot", run + 1)
					.isEqualTo(1);
		}
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private static <T> T get(Future<T> future) {
		try {
			return future.get();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
