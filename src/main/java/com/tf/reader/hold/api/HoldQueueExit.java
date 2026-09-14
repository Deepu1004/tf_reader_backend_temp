package com.tf.reader.hold.api;

/**
 * Published contract: drop every live hold a reader is currently queued in, because their
 * session just ended. Owned by the {@code hold} module; {@code auth} calls this on logout.
 *
 * <p>An ELITE hold-queue position is tied to the session that placed it, not to a durable
 * account - an institutional SAML reader has none. Closing the session is the reader leaving
 * every queue they were in immediately, rather than leaving a ticket nobody will ever come back
 * to claim.
 */
public interface HoldQueueExit {

	/**
	 * Cancels every live hold owned by {@code userId}, wherever it was queued.
	 *
	 * <p>Best-effort from the caller's side: a reader who never queued for anything, or whose
	 * queue positions have already lapsed, is simply a no-op.
	 *
	 * @param userId the reader whose session just ended
	 */
	void leaveAll(String userId);
}
