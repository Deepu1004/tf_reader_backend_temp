package com.tf.reader.reading.service;

// Every Redis key CopyLeaseImpl touches, built in one place — the item key and the
// token's reverse-index key must agree byte-for-byte or release(String) looks up the
// wrong counter.
final class LeaseKeys {

	private static final String ITEM_KEY_PREFIX = "lease:";
	static final String TOKEN_KEY_PREFIX = "lease:token:";
	static final String ALL_KEYS_PATTERN = "lease:*";

	private LeaseKeys() {
	}

	static String itemKey(String scope, String itemId) {
		// A null scope (from acquire() which has no institution context) produces
		// "lease::itemId" — the scope segment is deliberately empty rather than
		// the string "null", which Java concatenation would silently produce and
		// which could collide with a real institution named "null".
		String safeScope = scope == null ? "" : scope;
		return ITEM_KEY_PREFIX + safeScope + ":" + itemId;
	}

	static String tokenKey(String token) {
		return TOKEN_KEY_PREFIX + token;
	}

	// Reverses itemKey() for the reconciler's Redis-side scan — the only caller that ever
	// needs to go from a key back to the (scope, itemId) it was built from.
	record Parsed(String scope, String itemId) {
	}

	static Parsed parseItemKey(String key) {
		String rest = key.substring(ITEM_KEY_PREFIX.length());
		int colon = rest.indexOf(':');
		return colon < 0 ? new Parsed(null, rest) : new Parsed(rest.substring(0, colon), rest.substring(colon + 1));
	}
}
