package com.tf.reader.reading.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;

/**
 * Stub implementation — always grants a lease. Replace with the real Redis-backed
 * implementation once Deepak's lease service is wired up.
 */
@Service
class CopyLeaseImpl implements CopyLease {

	private static final Logger log = LoggerFactory.getLogger(CopyLeaseImpl.class);

	@Override
	public Optional<LeaseHandle> acquire(String itemId) {
		String leaseId = "stub_lease_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		log.info("[STUB] Acquired lease {} for item {}", leaseId, itemId);
		return Optional.of(new LeaseHandle(leaseId));
	}

	@Override
	public void release(String leaseId) {
		log.info("[STUB] Released lease {}", leaseId);
	}
}
