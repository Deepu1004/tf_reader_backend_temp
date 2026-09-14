package com.tf.reader.sync.api;

/**
 * Published contract: mark every download a reader holds for a title as no longer valid,
 * so offline devices learn on their next sync that the licence has ended.
 *
 * <p>Called by the loan module on return, expiry, and revocation. Callers must tolerate the
 * case where no download exists for the (userId, itemId) pair — not every loan produces a
 * download, and the absence of one is not an error.
 */
public interface DownloadInvalidation {

    /**
     * Flips {@code isValid = false} on every active download the user holds for this title.
     * No-op if the user has no download for this title.
     *
     * @param userId  the reader whose licence ended
     * @param itemId  the title (same value as {@code bookId} in the sync module)
     */
    void invalidate(String userId, String itemId);
}
