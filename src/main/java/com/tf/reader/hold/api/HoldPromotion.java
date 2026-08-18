package com.tf.reader.hold.api;

/**
 * Published contract: notify the hold module that a copy slot has been freed so it can
 * promote the next waiter. Owned by the {@code hold} module; the {@code loan} module
 * calls this after every return and after every expiry-sweep ending.
 *
 * <p>Promotion is best-effort from the loan module's perspective — if there is nobody
 * waiting the hold module simply does nothing. Failures must not abort the return or
 * sweep that triggered them.
 */
public interface HoldPromotion {

	/**
	 * Signal that one copy slot for {@code itemId} is now free. The hold module will
	 * promote the head of the wait queue (if any) into that slot and create their loan.
	 *
	 * @param itemId the title whose slot was just freed
	 */
	void promote(String itemId);
}
