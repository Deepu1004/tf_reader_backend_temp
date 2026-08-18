package com.tf.reader.loan.entity;

/**
 * How the reader's access to a title is bounded — decided by entitlement, never sent by the client
 * (D-009). Maps one-to-one from {@code catalogue.api.AccessLevel}:
 *
 * <ul>
 *   <li>{@code ENTITLED_UNLIMITED} → {@link #SUBSCRIPTION}</li>
 *   <li>{@code ENTITLED_CONCURRENT} → {@link #ELITE}</li>
 *   <li>{@code OPEN_ACCESS} → {@link #OPEN_ACCESS}</li>
 * </ul>
 *
 * <p>The translation lives in exactly one place (the create flow), so this enum stays a plain
 * vocabulary of our own domain rather than a mirror of the catalogue's.
 */
public enum LicenseModel {

	/** Unlimited concurrent access. No lease, no queue. {@code canPersist = true}. */
	SUBSCRIPTION,

	/** Copy-limited. Holds a read-queue slot (a lease) while active. {@code canPersist = false}. */
	ELITE,

	/** Freely available. Loan written for the audit trail, but no lease and no due date. */
	OPEN_ACCESS
}
