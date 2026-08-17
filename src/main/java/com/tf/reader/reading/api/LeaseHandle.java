package com.tf.reader.reading.api;

/**
 * A reference to one granted copy-lease in Redis. Carried on an Elite loan so the return
 * and expiry flows can release the exact slot that was acquired (D-018).
 *
 * <p>{@code leaseId} is minted by Deepak's lease service; it arrives null on our create
 * and is filled in during the hand-off.
 */
public record LeaseHandle(String leaseId) {
}
