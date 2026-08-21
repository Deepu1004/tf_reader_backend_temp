package com.tf.reader.reading.api;

import java.time.Instant;
import java.util.Optional;

/**
 * Published contract: hold a concurrent copy for the duration of a read.
 */
public interface CopyLease {

	Optional<LeaseHandle> claim(String scope, String itemId, int copies);

	Optional<LeaseHandle> acquire(String itemId);

	boolean extend(LeaseHandle handle, Instant until);

	void release(LeaseHandle handle);

	void release(String leaseId);

	void reassign(String scope, String itemId, String fromToken, String newToken, Instant until);

	int available(String scope, String itemId, int copies);
}
