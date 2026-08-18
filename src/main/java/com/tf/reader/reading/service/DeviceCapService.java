package com.tf.reader.reading.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.UpdateResult;

import com.tf.reader.reading.entity.DeviceFingerprint;

/**
 * The per-reader device limit.
 *
 * <p>The catalogue team declared this out of scope and dropped their device registry. We keep it for
 * individuals, derived from the fingerprint of the key that already arrives on every read — so it
 * depends on nothing from anybody and is not theirs to descope.
 *
 * <p><b>Enforced at read, not at borrow</b>, because the device key only arrives with a reading
 * session request. There is nothing to cap on any earlier.
 */
@Service
public class DeviceCapService {

	/** Field names, once. A typo in one of these is a filter that silently matches nothing. */
	private static final String USER_ID = "userId";
	private static final String DEVICES = "devices";
	private static final String FINGERPRINT = "devices.fingerprint";
	private static final String UPDATED_AT = "updatedAt";

	private final MongoTemplate mongo;
	private final Clock clock;
	private final int maxDevices;
	private final Duration staleAfter;

	public DeviceCapService(MongoTemplate mongo, Clock clock,
			@Value("${tnf.devices.max:5}") int maxDevices,
			@Value("${tnf.devices.stale-after:P90D}") Duration staleAfter) {
		this.mongo = mongo;
		this.clock = clock;
		this.maxDevices = maxDevices;
		this.staleAfter = staleAfter;
	}

	/**
	 * Records this device against the reader, if the cap allows it.
	 *
	 * <p>Returns a verdict rather than throwing, for now: the shared {@code ErrorCode} enum does not
	 * yet carry {@code DEVICE_LIMIT_REACHED}, and inventing a second error shape to work around that
	 * would be worse than returning a boolean. The broker turns {@code false} into the refusal once
	 * the code exists. <b>TODO:</b> propose {@code DEVICE_LIMIT_REACHED} to the owner of
	 * {@code common/error}, then throw from here instead.
	 *
	 * @param devicePublicKey the raw key bytes, already decoded by the caller
	 * @return true when the device is recorded and the read may proceed
	 */
	public boolean admit(String userId, byte[] devicePublicKey) {

		String fingerprint = fingerprintOf(devicePublicKey);
		Instant now = clock.instant();

		// A device we have already seen. Touch it and let it through — refusing a device the reader
		// was already using, just because the cap is now full, reads as a bug rather than a policy.
		UpdateResult touched = mongo.updateFirst(
				Query.query(Criteria.where(USER_ID).is(userId)
						.and(FINGERPRINT).is(fingerprint)),
				new Update()
						.set("devices.$.lastSeenAt", now)
						.set(UPDATED_AT, now),
				DeviceFingerprint.class);

		if (touched.getModifiedCount() > 0) {
			return true;
		}

		// A new device. ONE conditional update, where the filter IS the check.
		//
		// Mongo cannot express "array shorter than N" directly — $size is an exact match only, and
		// there is no size().lt() in Spring Data however much it looks like there should be. The
		// idiom is "the element at index N-1 does not exist", which holds only when the array has at
		// most N-1 entries, so pushing leaves at most N.
		//
		// The fingerprint-absent clause makes this safe against a race with the touch above.
		UpdateResult appended = mongo.upsert(
				Query.query(Criteria.where(USER_ID).is(userId)
						.and(DEVICES + "." + (maxDevices - 1)).exists(false)
						.and(FINGERPRINT).ne(fingerprint)),
				new Update()
						.push(DEVICES, new DeviceFingerprint.Device(fingerprint, now, now))
						.setOnInsert(USER_ID, userId)
						.setOnInsert("createdAt", now)
						.set(UPDATED_AT, now),
				DeviceFingerprint.class);

		return appended.getModifiedCount() > 0 || appended.getUpsertedId() != null;
	}

	/**
	 * Drops devices nobody has read on for a long time.
	 *
	 * <p>A separate scheduled job on purpose. Pruning inside the conditional update above would make
	 * the cap non-deterministic: the same request would pass or fail depending on when an unrelated
	 * device was last seen.
	 */
	@Scheduled(cron = "${tnf.devices.prune-cron:0 0 3 * * *}")
	public void pruneStale() {
		Instant cutoff = clock.instant().minus(staleAfter);
		mongo.updateMulti(
				new Query(),
				new Update().pull(DEVICES,
						Query.query(Criteria.where("lastSeenAt").lt(cutoff))),
				DeviceFingerprint.class);
	}

	/** SHA-256 of the raw bytes. Stable for the same device, and discloses nothing about the key. */
	private static String fingerprintOf(byte[] raw) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
			return "sha256:" + HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
