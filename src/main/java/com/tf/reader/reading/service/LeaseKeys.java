package com.tf.reader.reading.service;

// Every Redis key CopyLeaseImpl touches, built in one place — the item key and the
// token's reverse-index key must agree byte-for-byte or release(String) looks up the
// wrong counter.
final class LeaseKeys {

	private LeaseKeys() {
	}

	static String itemKey(String scope, String itemId) {
		return "lease:" + scope + ":" + itemId;
	}

	static String tokenKey(String token) {
		return "lease:token:" + token;
	}
}
