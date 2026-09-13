package com.tf.reader.hold.service;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

// Every Redis key hold touches, built in one place. QueueService,
// PromotionService and AvailabilityQueryImpl all need the identical string
// for the same title's queue — building it three separate times is exactly
// how one of them drifts by a typo and starts reading a different key.
public final class QueueKeys {

    private static final String QUEUE_KEY_PREFIX = "queue:";

    // The reconciler's Redis-side scan, for items whose queue Redis still knows about but
    // Mongo no longer has any QUEUED hold for at all.
    public static final String ALL_QUEUE_KEYS_PATTERN = QUEUE_KEY_PREFIX + "*";

    private QueueKeys() {
    }

    public static String requireScope(String scope) {
        if (scope == null || scope.isBlank()) {
            // Individual (OIDC, no-institution) tokens carry no institutionId.
            // This fires instead of silently building "queue:null:itemId".
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No institution scope on this token");
        }
        return scope;
    }

    public static String queueKey(String scope, String itemId) {
        return QUEUE_KEY_PREFIX + scope + ":" + itemId;
    }

    public static String ticketKey(String scope, String itemId) {
        return "queueseq:" + scope + ":" + itemId;
    }

    public static String promoteLockKey(String scope, String itemId) {
        return "promote:" + scope + ":" + itemId;
    }

    public static String member(String userId) {
        return "u:" + userId;
    }

    public static String userOf(String member) {
        // Parses data this class itself wrote into Redis. A member that
        // doesn't start with the "u:" prefix means something else wrote to
        // this key, or the data is corrupt — fail loudly rather than throw
        // an unhelpful StringIndexOutOfBoundsException three lines away.
        if (member == null || !member.startsWith("u:")) {
            throw new IllegalStateException("Not a queue member: " + member);
        }
        return member.substring(2);
    }

    // Reverses queueKey() for the reconciler's Redis-side scan — the only caller that ever
    // needs to go from a key back to the (scope, itemId) it was built from.
    public record Parsed(String scope, String itemId) {
    }

    public static Parsed parseQueueKey(String key) {
        String rest = key.substring(QUEUE_KEY_PREFIX.length());
        int colon = rest.indexOf(':');
        return colon < 0 ? new Parsed(null, rest) : new Parsed(rest.substring(0, colon), rest.substring(colon + 1));
    }
}
