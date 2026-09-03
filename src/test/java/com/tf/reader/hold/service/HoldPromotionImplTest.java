package com.tf.reader.hold.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

// The published interface now carries the scope directly, so there is nothing left for this
// class to decide — it is a pure pass-through. PromotionService's own tests cover the real logic.
class HoldPromotionImplTest {

    private final PromotionService promotion = mock(PromotionService.class);
    private final HoldPromotionImpl impl = new HoldPromotionImpl(promotion);

    @Test
    void promotesTheGivenScopeAndItemWithNoLeaseTokenToReassign() {
        impl.promote("inst_a", "item_1");

        verify(promotion).promoteNext("inst_a", "item_1", null);
        verifyNoMoreInteractions(promotion);
    }
}
