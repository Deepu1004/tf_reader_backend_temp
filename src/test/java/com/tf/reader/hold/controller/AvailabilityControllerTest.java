package com.tf.reader.hold.controller;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.hold.api.AvailabilityQuery;
import com.tf.reader.hold.api.AvailabilitySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvailabilityControllerTest {

    private final AvailabilityQuery availability = mock(AvailabilityQuery.class);
    private final EntitlementQuery entitlements = mock(EntitlementQuery.class);
    private final AvailabilityController controller = new AvailabilityController(availability, entitlements);
    private final CurrentUser me = new CurrentUser("user_a", UserType.INSTITUTION, "inst_1", List.of(), List.of());

    @Test
    void passesTheRealCopyCountThrough() {
        when(entitlements.check(any(), eq("item_1")))
                .thenReturn(new EntitlementDecision(true, AccessLevel.ENTITLED_CONCURRENT, "ent_1", 2, 14, null, null));
        var snapshot = new AvailabilitySnapshot(1, 3, null, Instant.now());
        when(availability.forItem("inst_1", "item_1", 2)).thenReturn(snapshot);

        assertThat(controller.forItem(me, "item_1")).isSameAs(snapshot);
    }

    @Test
    void anEntitlementFailureBecomesANullCopyCountNeverA500() {
        when(entitlements.check(any(), any())).thenThrow(new RuntimeException("catalogue unreachable"));
        var unknown = AvailabilitySnapshot.unknown(Instant.now());
        when(availability.forItem("inst_1", "item_1", null)).thenReturn(unknown);

        var result = controller.forItem(me, "item_1");

        assertThat(result).isSameAs(unknown);
        verify(availability).forItem("inst_1", "item_1", null);
    }
}
