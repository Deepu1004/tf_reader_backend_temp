package com.tf.reader.hold.api;

import java.util.List;

// Published contract: where does this person stand in the queue? Library
// calls this to render the "your holds" shelf.
//
// Empty list, never null, never throws — the shelf renders holds: [] by
// design when a reader has none.
public interface HoldSnapshotQuery {
    List<HoldSnapshot> holdsFor(String userId);
}
