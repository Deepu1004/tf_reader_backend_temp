package com.tf.reader.library.api;

/**
 * Why a change log entry was written.
 *
 * <p>The eight values are the wire enum from the API Reference, name for name. The app switches on
 * these strings, so a rename here is a breaking change to team1's client.
 *
 * <p><b>In the published package, not in {@code library/entity}.</b> The loan, hold and reading
 * modules all need this enum to call {@link ChangeLog#record}, and reaching into another module's
 * entity package is the coupling an {@code api} package exists to prevent.
 *
 * <p>{@link #ENTITLEMENT_REVOKED} is why the feed exists at all. Every other reason describes
 * something the reader did and therefore already knows about; a revocation is the only way the app
 * learns that a book it is currently displaying has stopped being readable.
 */
public enum ChangeReason {

	LOAN_CREATED,

	/**
	 * The copy went back. Also how a revocation is recorded, because {@code LoanStatus} has no
	 * {@code REVOKED} — per D-017, "the user returned" and "access was taken away" are the same
	 * ending, both stamped with {@code returnedAt}.
	 */
	LOAN_RETURNED,

	/** The clock ran out. Written by the expiry sweeper rather than by a reader's action. */
	LOAN_EXPIRED,

	HOLD_PLACED,
	HOLD_CANCELLED,
	HOLD_PROMOTED,

	/** The offer window closed with nobody accepting, and the copy was passed on. */
	HOLD_OFFER_EXPIRED,

	/**
	 * The entitlement behind a loan was withdrawn while the reader still held it. The one reason a
	 * client cannot infer from its own actions, and the reason this feed is not optional.
	 */
	ENTITLEMENT_REVOKED

}
