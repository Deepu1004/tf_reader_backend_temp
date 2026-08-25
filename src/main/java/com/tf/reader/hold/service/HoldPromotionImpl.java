package com.tf.reader.hold.service;

import com.tf.reader.hold.api.HoldPromotion;
import com.tf.reader.hold.entity.Hold;
import com.tf.reader.hold.entity.HoldStatus;
import com.tf.reader.hold.repository.HoldRepository;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the HoldPromotion contract loan calls after every return and expiry-sweep
 * ending. The published signature carries no institution scope, but hold's queues are scoped
 * per institution — so this fans out over every institution currently queuing for the item
 * rather than assuming there is only one.
 *
 * <p>That gap also means the reassign-never-drops guarantee in {@link PromotionService} does not
 * apply on this path: nothing here tells us whose lease was just freed, so every promotion
 * through this method is a fresh {@code claim}, never a {@code reassign}. Raise with Shashank —
 * {@code HoldPromotion.promote} needs a scope parameter (and ideally the freed lease token) to
 * close this.
 */
@Service
class HoldPromotionImpl implements HoldPromotion {

	private final HoldRepository holds;
	private final PromotionService promotion;

	HoldPromotionImpl(HoldRepository holds, PromotionService promotion) {
		this.holds = holds;
		this.promotion = promotion;
	}

	@Override
	public void promote(String itemId) {
		Set<String> scopes = holds.findByItemIdAndStatus(itemId, HoldStatus.QUEUED).stream()
				.map(Hold::getScope)
				.collect(Collectors.toSet());
		scopes.forEach(scope -> promotion.promoteNext(scope, itemId, null));
	}
}
