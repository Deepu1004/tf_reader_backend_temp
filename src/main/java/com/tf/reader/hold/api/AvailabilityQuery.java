package com.tf.reader.hold.api;

/**
 * Published contract: query queue availability context for a copy-limited title.
 *
 * This method is best-effort and MUST NOT throw for ordinary data problems.
 * When the caller cannot determine availability (no copy limit, entitlement
 * check failed, or an upstream omission), pass `null` for `copies` and the
 * implementation should omit availability rather than guessing.
 */
public interface AvailabilityQuery {

	/**
	 * Returns copies free / availability context for an item in the given scope.
	 *
	 * @param scope     institution or tenant scope
	 * @param itemId    item identifier
	 * @param copies    optional copy limit (null when unknown)
	 */
	AvailabilitySnapshot forItem(String scope, String itemId, Integer copies);
}
