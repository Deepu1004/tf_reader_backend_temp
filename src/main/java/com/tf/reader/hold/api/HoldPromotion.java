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
	 * Signal that one copy slot for {@code itemId}, in {@code scope}, is now free. The hold
	 * module will promote the head of that institution's wait queue (if any) into the slot
	 * and create their loan.
	 *
	 * <p>No lease token: by the time loan calls this, the freed copy's lease has already been
	 * released, so there is nothing to reassign — every promotion through this method is a
	 * fresh claim.
	 *
	 * @param scope  the institution whose copy pool just gained a free slot
	 * @param itemId the title whose slot was just freed
	 */
	void promote(String scope, String itemId);
}
