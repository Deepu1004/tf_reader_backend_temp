package com.tf.reader.loan.api;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.SubjectRef;

/**
 * Published contract: create the licence for a read, carrying its already-claimed lease ID.
 */
public interface LicenceCommand {

	/**
	 * Create the licence for a read that has already passed every check AND, for elite, already holds a copy.
	 *
	 * <p>{@code leaseId} is non-null for {@code ENTITLED_CONCURRENT} (ELITE) and null for the other two tiers,
	 * so it means exactly one thing on the row: "this tier has no copy limit".
	 *
	 * <p>Idempotent on {@code (userId, itemId)} while a licence is live.
	 */
	LicenceView create(SubjectRef subject, String itemId, AccessLevel accessLevel, int loanPeriodDays, String leaseId);

	/**
	 * True if a loan for this (userId, itemId) exists with status EXPIRED — meaning the system
	 * reclaimed the seat rather than the user returning it. Used by the read broker to reject
	 * silent re-mints: a STREAM re-check is not the same as a fresh borrow.
	 */
	boolean hasExpiredLoan(String userId, String itemId);
}
