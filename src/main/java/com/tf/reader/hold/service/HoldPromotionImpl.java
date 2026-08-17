package com.tf.reader.hold.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tf.reader.hold.api.HoldPromotion;

/**
 * Stub implementation — logs the promotion signal and does nothing else. Replace with
 * the real hold-queue promotion once Khushi's hold module is wired up.
 */
@Service
class HoldPromotionImpl implements HoldPromotion {

	private static final Logger log = LoggerFactory.getLogger(HoldPromotionImpl.class);

	@Override
	public void promote(String itemId) {
		log.info("[STUB] Promotion signalled for item {}", itemId);
	}
}
