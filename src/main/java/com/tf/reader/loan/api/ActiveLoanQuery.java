package com.tf.reader.loan.api;

/**
 * Published contract: does this reader currently hold an active copy of this title?
 * Called in-process by the {@code reading} and {@code library} modules — no HTTP, no token.
 *
 * <p>Derives active status from {@code dueAt} on the server clock, never from the raw
 * {@code status} field, so a loan that has passed its due date is inactive immediately —
 * not after the next sweeper tick (D-006).
 */
public interface ActiveLoanQuery {

	/**
	 * @return true iff the reader holds an {@code ACTIVE} loan for this item and
	 *         ({@code dueAt} is null or still in the future on the server clock)
	 */
	boolean isActive(String userId, String itemId);
}
