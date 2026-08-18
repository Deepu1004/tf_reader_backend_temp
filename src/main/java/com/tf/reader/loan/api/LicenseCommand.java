package com.tf.reader.loan.api;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.SubjectRef;

/**
 * Published contract: create the license for a read, carrying its already-claimed lease ID.
 */
public interface LicenseCommand {

	/**
	 * Create the license for a read that has already passed every check AND, for elite, already holds a copy.
	 *
	 * <p>{@code leaseId} is non-null for {@code ENTITLED_CONCURRENT} (ELITE) and null for the other two tiers,
	 * so it means exactly one thing on the row: "this tier has no copy limit".
	 *
	 * <p>Idempotent on {@code (userId, itemId)} while a license is live.
	 */
	LicenseView create(SubjectRef subject, String itemId, AccessLevel accessLevel, int loanPeriodDays, String leaseId);
}
