package com.tf.reader.hold.service;

import com.tf.reader.hold.api.HoldPromotion;
import org.springframework.stereotype.Service;

/**
 * Implementation of the HoldPromotion contract loan calls after every return and expiry-sweep
 * ending. The published signature now carries the institution scope directly, so this no longer
 * has to guess by scanning every institution queued for the item.
 */
@Service
class HoldPromotionImpl implements HoldPromotion {

	private final PromotionService promotion;

	HoldPromotionImpl(PromotionService promotion) {
		this.promotion = promotion;
	}

	@Override
	public void promote(String scope, String itemId) {
		// No lease token: return/expiry already released the copy before calling this, so
		// there is nothing to reassign — every promotion here is a fresh claim.
		promotion.promoteNext(scope, itemId, null);
	}
}
