package com.tf.reader.hold.service;

import com.tf.reader.hold.api.HoldView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// One shape, one code path — this only maps QueueService's own computation,
// it never recomputes anything.
class HoldSnapshotQueryImplTest {

    private final QueueService queue = mock(QueueService.class);
    private final HoldSnapshotQueryImpl impl = new HoldSnapshotQueryImpl(queue);

    @Test
    void mapsEachHoldViewToAHoldSnapshotWithTheSameFields() {
        var view = new HoldView("hold_1", "item_1", "QUEUED", 2, 5, 14, Instant.parse("2026-08-17T09:00:00Z"), null);
        when(queue.holdsFor("user_a")).thenReturn(List.of(view));

        var snapshots = impl.holdsFor("user_a");

        assertThat(snapshots).hasSize(1);
        var snapshot = snapshots.get(0);
        assertThat(snapshot.holdId()).isEqualTo("hold_1");
        assertThat(snapshot.position()).isEqualTo(2);
        assertThat(snapshot.queueLength()).isEqualTo(5);
        assertThat(snapshot.estimatedWaitDays()).isEqualTo(14);
    }

    @Test
    void anEmptyListStaysEmptyNeverNull() {
        when(queue.holdsFor("user_a")).thenReturn(List.of());

        assertThat(impl.holdsFor("user_a")).isNotNull().isEmpty();
    }
}
