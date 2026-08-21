package com.tf.reader.loan.entity;

/**
 * Lifecycle states of a loan — the frozen flambeau {@code LoanStatus} enum exactly (D-016/D-017).
 *
 * <p>There is deliberately no {@code REVOKED}: "the user returned" and "access was taken away" are
 * the same ending, both stored as {@link #RETURNED} with {@code returnedAt} set. See DECISIONS.md
 * D-004 (superseded) and D-017.
 */
public enum LoanStatus {

	/** The loan is live. Exactly one ACTIVE loan may exist per (userId, itemId) — partial unique index. */
	ACTIVE,

	/** The clock ran out. Stamped by the expiry sweeper; sets {@code expiredAt}. */
	EXPIRED,

	/** The user gave the copy back, or access was revoked. Sets {@code returnedAt}. Elite only. */
	RETURNED
}
