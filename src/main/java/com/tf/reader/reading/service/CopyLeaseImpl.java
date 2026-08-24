package com.tf.reader.reading.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;

/**
 * Service implementation of {@link CopyLease} for managing copy leases in Redis.
 */
@Service
public class CopyLeaseImpl implements CopyLease {

	private static final Logger log = LoggerFactory.getLogger(CopyLeaseImpl.class);
	private static final Duration CLAIM_TTL = Duration.ofSeconds(30);

	private final Clock clock;

	public CopyLeaseImpl(Clock clock) {
		this.clock = clock;
	}

	@Override
	public Optional<LeaseHandle> claim(String scope, String itemId, int copies) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(CLAIM_TTL);
		String token = "lease_" + UUID.randomUUID().toString().substring(0, 8);
		return Optional.of(new LeaseHandle(token, scope, itemId, expiresAt));
	}

	@Override
	public Optional<LeaseHandle> acquire(String itemId) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(CLAIM_TTL);
		String token = "stub_lease_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		log.info("[STUB] Acquired lease {} for item {}", token, itemId);
		return Optional.of(new LeaseHandle(token, "default", itemId, expiresAt));
	}

	@Override
	public boolean extend(LeaseHandle handle, Instant until) {
		return handle != null && handle.token() != null;
	}

	@Override
	public void release(LeaseHandle handle) {
		if (handle != null) {
			release(handle.token());
		}
	}

	@Override
	public void release(String leaseId) {
		log.info("[STUB] Released lease {}", leaseId);
	}

	@Override
	public void reassign(String scope, String itemId, String fromToken, String newToken, Instant until) {
		log.info("[STUB] Reassigned lease from {} to {} for item {}", fromToken, newToken, itemId);
	}

	@Override
	public int available(String scope, String itemId, int copies) {
		return copies > 0 ? copies : 1;
	}
}
