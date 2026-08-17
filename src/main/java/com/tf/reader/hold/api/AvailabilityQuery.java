package com.tf.reader.hold.api;

/**
 * Published contract: query queue availability context for a copy-limited title.
 */
public interface AvailabilityQuery {

	/**
	 * Returns copies free, queue length, and this reader's position if they are in line.
	 * Used to provide context on a 409 NO_COPIES_AVAILABLE refusal.
	 */
	AvailabilitySnapshot forItem(String scope, String itemId, int copies);
}
