package com.tf.reader.reading.service;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.reading.entity.DeviceFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the cap holds under the exact race it exists for: several new devices arriving in the
 * same instant. Runs against a real Mongo (Testcontainers) — an in-memory fake cannot prove a
 * conditional update actually excludes the race the way it looks like it should on paper.
 */
@Import(TestcontainersConfiguration.class)
@DataMongoTest
class DeviceCapServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneOffset.UTC);
	private static final int MAX_DEVICES = 2;

	@Autowired
	private MongoTemplate mongo;

	private DeviceCapService cap;

	@BeforeEach
	void setUp() {
		// Documents only, never dropCollection: dropping the collection also drops the unique
		// index on userId, which auto-index-creation recreates once at startup and not again —
		// so a later test would run with no uniqueness guard at all and this would fail silently.
		mongo.remove(new Query(), DeviceFingerprint.class);
		// Constructed directly rather than autowired, so the @Value defaults on the real bean
		// are not in play — this test fixes the cap at 2 so the race below needs only 3 racers.
		cap = new DeviceCapService(mongo, CLOCK, MAX_DEVICES, Duration.ofDays(90));
	}

	private static byte[] key(String label) {
		return ("device-" + label).getBytes(StandardCharsets.UTF_8);
	}

	@Test
	void theSameKeyTwiceIsOneDeviceNotTwo() {
		cap.admit("user_9c2", key("a"));
		cap.admit("user_9c2", key("a"));

		assertThat(devicesOf("user_9c2")).hasSize(1);
	}

	@Test
	void aSecondDistinctDeviceIsAllowedUpToTheCap() {
		cap.admit("user_9c2", key("a"));
		cap.admit("user_9c2", key("b"));

		assertThat(devicesOf("user_9c2")).hasSize(2);
	}

	@Test
	void theCapRefusesRatherThanExceeding() {
		cap.admit("user_9c2", key("a"));
		cap.admit("user_9c2", key("b"));

		boolean admitted = cap.admit("user_9c2", key("c"));

		assertThat(admitted).isFalse();
		assertThat(devicesOf("user_9c2")).hasSize(2); // not 3
	}

	@Test
	void anAlreadyKnownDeviceIsStillAdmittedWhenTheCapIsFull() {
		cap.admit("user_9c2", key("a"));
		cap.admit("user_9c2", key("b"));

		// The cap is full, but this is one of the two already-known devices. Refusing here would
		// lock a reader out of a device they were already reading on.
		boolean admitted = cap.admit("user_9c2", key("a"));

		assertThat(admitted).isTrue();
		assertThat(devicesOf("user_9c2")).hasSize(2);
	}

	@Test
	void threeDevicesArrivingTogetherWithOneSlotFreeAdmitExactlyOne() throws Exception {
		// One slot already used, one free.
		cap.admit("user_9c2", key("existing"));

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(3);
		List<Future<Boolean>> futures = new ArrayList<>();

		for (int i = 0; i < 3; i++) {
			byte[] racerKey = key("racer-" + i);
			futures.add(pool.submit(() -> {
				start.await();
				return cap.admit("user_9c2", racerKey);
			}));
		}
		start.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

		long admitted = futures.stream().map(DeviceCapServiceTest::get).filter(Boolean.TRUE::equals).count();

		// Read-then-write would admit all three here — that is exactly the race the conditional
		// update exists to close.
		assertThat(admitted).isEqualTo(1);
		assertThat(devicesOf("user_9c2")).hasSize(2);
	}

	@Test
	void staleDevicesArePrunedOnSchedule() {
		cap.admit("user_9c2", key("a"));
		ageLastSeenBeyondNinetyDays("user_9c2", fingerprintOf(key("a")));

		cap.pruneStale();

		assertThat(devicesOf("user_9c2")).isEmpty();
	}

	@Test
	void pruningDoesNotTouchARecentlySeenDevice() {
		cap.admit("user_9c2", key("a"));

		cap.pruneStale();

		assertThat(devicesOf("user_9c2")).hasSize(1);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private List<DeviceFingerprint.Device> devicesOf(String userId) {
		DeviceFingerprint doc = mongo.findOne(
				Query.query(Criteria.where("userId").is(userId)), DeviceFingerprint.class);
		return doc == null ? List.of() : doc.getDevices();
	}

	/** Back-dates the one device recorded so far, so the prune job has something to remove. */
	private void ageLastSeenBeyondNinetyDays(String userId, String fingerprint) {
		Instant longAgo = CLOCK.instant().minus(Duration.ofDays(120));
		mongo.updateFirst(
				Query.query(Criteria.where("userId").is(userId).and("devices.fingerprint").is(fingerprint)),
				new Update().set("devices.$.lastSeenAt", longAgo),
				DeviceFingerprint.class);
	}

	private static String fingerprintOf(byte[] raw) {
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256").digest(raw);
			return "sha256:" + java.util.HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static <T> T get(Future<T> future) {
		try {
			return future.get();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
