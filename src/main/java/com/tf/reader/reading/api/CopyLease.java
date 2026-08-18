package com.tf.reader.reading.api;

import java.util.Optional;

/**
 * Published contract: acquire and release a concurrent-copy slot for an Elite loan.
 * Owned by the {@code reading} module; backed by Redis. The {@code loan} module calls
 * this port — it never touches Redis directly (D-010).
 *
 * <p>All implementations must be thread-safe; the loan service may call acquire/release
 * concurrently for different items.
 */
public interface CopyLease {

	/**
	 * Attempt to acquire one copy slot for {@code itemId}.
	 *
	 * @return a {@link LeaseHandle} if a slot was free, or {@link Optional#empty()} if all
	 *         copies are currently held. The loan service maps empty to
	 *         {@code 409 NO_COPIES_AVAILABLE}.
	 */
	Optional<LeaseHandle> acquire(String itemId);

	/**
	 * Release the slot identified by {@code leaseId} back to the pool. Called on return and
	 * by the expiry sweeper. Must be safe to call even if the lease has already expired in
	 * Redis — idempotent, never throws.
	 */
	void release(String leaseId);
}
