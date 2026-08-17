package com.tf.reader.reading.service;

import org.springframework.stereotype.Service;

/**
 * Rebuilds Redis lease state from Mongo active licences and offers.
 */
@Service
public class ReconcilerService {

	/**
	 * Immediate single-item reconciliation trigger when extending a lease fails.
	 */
	public void reconcile(String itemId) {
		// Reconciler stub for Redis state rebuild
	}
}
