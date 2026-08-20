package com.tf.reader.library.api;

import java.time.Instant;

/**
 * Something that changed for one reader, handed to {@link ChangeLog} by the module that changed it.
 *
 * <p><b>Carries no sequence.</b> The library module allocates that at write time, because
 * monotonic-per-reader ordering can only be guaranteed by whoever owns the counter — a caller
 * supplying its own would have to coordinate with every other caller.
 *
 * <p>Prefer {@link #forLoan} and {@link #forHold} over the canonical constructor: they make it
 * impossible to hang a {@code holdId} off a loan event, which is the kind of mistake that surfaces
 * as a client quietly ignoring an entry it cannot interpret.
 *
 * @param userId     whose feed this belongs to
 * @param reason     what happened
 * @param itemId     the title it happened to. Never null: every reason is about a title
 * @param loanId     the loan, for the loan reasons; null otherwise
 * @param holdId     the hold, for the hold reasons; null otherwise
 * @param occurredAt when it happened. Truncated to whole seconds on write, per the wire convention
 */
public record ChangeRecord(
		String userId,
		ChangeReason reason,
		String itemId,
		String loanId,
		String holdId,
		Instant occurredAt) {

	public ChangeRecord {
		requireText(userId, "userId");
		requireText(itemId, "itemId");
		if (reason == null) {
			throw new IllegalArgumentException("reason is required");
		}
		if (occurredAt == null) {
			throw new IllegalArgumentException("occurredAt is required");
		}
	}

	/** A loan transition: created, returned or expired. */
	public static ChangeRecord forLoan(String userId, ChangeReason reason, String itemId,
			String loanId, Instant occurredAt) {
		requireText(loanId, "loanId");
		return new ChangeRecord(userId, reason, itemId, loanId, null, occurredAt);
	}

	/** A hold transition: placed, cancelled, promoted, or its offer lapsed. */
	public static ChangeRecord forHold(String userId, ChangeReason reason, String itemId,
			String holdId, Instant occurredAt) {
		requireText(holdId, "holdId");
		return new ChangeRecord(userId, reason, itemId, null, holdId, occurredAt);
	}

	/**
	 * A revocation, which belongs to a loan the reader still holds.
	 *
	 * <p>Separate from {@link #forLoan} only so the call site reads as what it is at the two places
	 * that might write it — the reconciler, and possibly the read broker (still open, task 29).
	 */
	public static ChangeRecord forRevocation(String userId, String itemId, String loanId,
			Instant occurredAt) {
		return forLoan(userId, ChangeReason.ENTITLEMENT_REVOKED, itemId, loanId, occurredAt);
	}

	/**
	 * Rejected at construction rather than at write time, because a record missing its reader or its
	 * title cannot be filed into anybody's feed and the caller is the only one who still knows what
	 * it was meant to say.
	 */
	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
	}

}
