package com.tf.reader.hold.api;

/**
 * Published contract: join the wait queue for a copy-limited title. Owned by the
 * {@code hold} module; the {@code reading} module calls this when a reading-session
 * request finds every copy taken, so the reader is queued in the same call instead of
 * needing a separate {@code POST /api/v1/holds}.
 *
 * <p>Same semantics as joining through the HTTP endpoint: already queued returns the
 * existing place rather than moving anyone to the back of the line, and entitlement is
 * re-checked here independently of whatever the caller already verified.
 */
public interface QueueJoin {

    /**
     * @param userId the reader
     * @param scope  institution scope
     * @param itemId the title with no free copy
     * @return the reader's place in the queue, and whether this call created it
     */
    JoinResult join(String userId, String scope, String itemId);

    record JoinResult(HoldView hold, boolean created) {
    }
}
