package com.tf.reader.hold.service;

import com.tf.reader.hold.entity.Hold;
import com.tf.reader.hold.entity.HoldStatus;
import com.tf.reader.hold.repository.HoldRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

// The published interface carries no scope, so this fans out over every
// institution currently queuing for the item — the real logic is
// PromotionService's, tested there.
class HoldPromotionImplTest {

    private final HoldRepository holds = mock(HoldRepository.class);
    private final PromotionService promotion = mock(PromotionService.class);
    private final HoldPromotionImpl impl = new HoldPromotionImpl(holds, promotion);

    @Test
    void promotesEveryInstitutionQueuingForTheItem() {
        Hold instA = Hold.queued("user_a", "inst_a", "item_1", 1, Instant.now());
        Hold instB = Hold.queued("user_b", "inst_b", "item_1", 1, Instant.now());
        when(holds.findByItemIdAndStatus("item_1", HoldStatus.QUEUED)).thenReturn(List.of(instA, instB));

        impl.promote("item_1");

        verify(promotion).promoteNext(eq("inst_a"), eq("item_1"), isNull());
        verify(promotion).promoteNext(eq("inst_b"), eq("item_1"), isNull());
        verifyNoMoreInteractions(promotion);
    }

    @Test
    void doesNothingWhenNobodyIsQueuingForTheItem() {
        when(holds.findByItemIdAndStatus("item_1", HoldStatus.QUEUED)).thenReturn(List.of());

        impl.promote("item_1");

        verifyNoInteractions(promotion);
    }
}
