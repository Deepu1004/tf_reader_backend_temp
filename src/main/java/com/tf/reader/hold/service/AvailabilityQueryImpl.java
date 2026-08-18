package com.tf.reader.hold.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.tf.reader.hold.api.AvailabilityQuery;
import com.tf.reader.hold.api.AvailabilitySnapshot;

/** Stub — always reports copies available equal to the limit, nobody queued. */
@Service
class AvailabilityQueryImpl implements AvailabilityQuery {

	private final Clock clock;

	AvailabilityQueryImpl(Clock clock) {
		this.clock = clock;
	}

	@Override
	public AvailabilitySnapshot forItem(String scope, String itemId, Integer copies) {
		Instant now = Instant.now(clock);
		if (copies == null) {
			return AvailabilitySnapshot.unknown(now);
		}
		return new AvailabilitySnapshot(copies, 0, null, now);
	}
}
