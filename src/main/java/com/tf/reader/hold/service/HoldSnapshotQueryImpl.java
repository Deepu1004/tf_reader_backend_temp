package com.tf.reader.hold.service;

import com.tf.reader.hold.api.HoldSnapshot;
import com.tf.reader.hold.api.HoldSnapshotQuery;
import org.springframework.stereotype.Service;

import java.util.List;

// Implementation of the HoldSnapshotQuery contract — a thin adapter over
// QueueService.holdsFor, the one place a reader's holds are actually
// computed. One shape, one code path; two would drift.
@Service
public class HoldSnapshotQueryImpl implements HoldSnapshotQuery {

    private final QueueService queue;

    public HoldSnapshotQueryImpl(QueueService queue) {
        this.queue = queue;
    }

    @Override
    public List<HoldSnapshot> holdsFor(String userId) {
        return queue.holdsFor(userId).stream().map(HoldSnapshot::from).toList();
    }
}
