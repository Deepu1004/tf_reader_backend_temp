package com.tf.reader.hold.api;

// Published contract: promote the next person when a copy frees up. Loan
// calls this on every return and every expiry.
//
// PROPOSAL: the published shape (per the contract draft) is
// promoteNext(scope, itemId) with no fromUserId. With five copies out, the
// lease set has five rows and nothing says which one to rename — reassigning
// without a "from" can't avoid a release-then-acquire gap where the copy
// reads as free. Raise this before relying on it; until then this is the
// signature hold's own code calls.
public interface HoldPromotion {

    // fromUserId is the reader who just gave the copy back — null for a
    // copy nobody currently holds (e.g. the very first grant). False means
    // nobody was waiting, the copy is genuinely free. True means it has
    // already been handed to the next reader in line.
    boolean promoteNext(String scope, String itemId, String fromUserId);
}
