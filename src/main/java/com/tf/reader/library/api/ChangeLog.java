package com.tf.reader.library.api;

/**
 * Published contract: record that something changed for a reader, so their app finds out.
 *
 * <p><b>Who calls this.</b> The loan module on create, return and expiry; the hold module on place,
 * cancel, promotion and offer lapse. Whether the reading module writes
 * {@link ChangeReason#ENTITLEMENT_REVOKED} is still open (task 29) — it matters for a downloaded
 * title, where a refusal at session time never reaches the device at all.
 *
 * <p><b>Why the interface exists.</b> The {@code changeLog} collection belongs to the library
 * module, and three other lanes need to write into it. Without a published port they would either
 * import a collection they do not own or leave the event unwritten — and an unwritten ending means a
 * returned book stays readable on a device until the next full library fetch.
 *
 * <p><b>Call it after the state write, not before.</b> An entry for a loan that failed to save tells
 * the app a book is readable when it is not. The opposite ordering — state written, record fails —
 * is recoverable, because {@code GET /api/v1/library} reads the real loans and holds rather than the
 * feed, so a missed entry is a delay rather than a wrong answer.
 *
 * <p><b>It will not throw.</b> A reader whose return succeeded must never be told "returning this
 * book failed" because a feed write failed, and whoever debugs that should not start in this lane.
 * Callers therefore need no try/catch and must not treat this as part of their transaction.
 *
 * <p>Recording is <b>not idempotent</b>: calling it twice writes two entries and the app applies the
 * same transition twice. That is harmless for every value of {@link ChangeReason}, all of which
 * converge on re-apply, but it is not licence to call this inside a retry loop.
 */
public interface ChangeLog {

	/**
	 * Writes one entry and returns the sequence it was given.
	 *
	 * <p>The returned sequence is the reader's newest, so a caller that wants to hand a client a
	 * cursor for this exact moment can use it rather than querying for it again.
	 *
	 * @return the allocated sequence, or {@code 0} if the entry could not be written. Zero is never
	 *         a real sequence — allocation starts at one — so it is safe to read as "not recorded",
	 *         and it is the same value a cursor uses to mean "from the beginning"
	 */
	long record(ChangeRecord change);

}
