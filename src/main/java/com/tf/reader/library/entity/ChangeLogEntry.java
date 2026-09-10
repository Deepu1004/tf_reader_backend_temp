package com.tf.reader.library.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.tf.reader.library.api.ChangeReason;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One thing that changed for one reader, in the order it changed.
 *
 * <p><b>{@code sequence} is ordering, not a count.</b> It is monotonic per reader and allocated by
 * the sequence dispenser, never derived from a timestamp: two events in the same millisecond have
 * no order under a clock, and a clock that steps backwards reshuffles a feed the client has already
 * consumed. The client stores the last sequence it saw and resumes from it exactly, so the ordering
 * has to be stable forever.
 *
 * <p><b>The compound index is unique, and that is the mechanism rather than a safety net.</b> Two
 * rows sharing a number means a device silently skips an event forever, so the second write fails
 * loudly instead. It is also what makes read-then-write allocation unusable: that version does not
 * merely race, it loses changes.
 *
 * <p>The index serves both repository queries — a feed page is a range scan on {@code sequence}
 * within one {@code userId}, and the high-water mark is that range's last entry.
 *
 * <p><b>No TTL, deliberately.</b> Rows are never trimmed. A reader with two devices must get the
 * same answer twice, and a device restored from a backup with an old cursor must be able to catch up
 * from the beginning — both need the history intact. In an 8-week prototype the log stays small; in
 * a real system this is where a retention policy gets decided, and a written-down "we chose not to"
 * beats an undocumented TTL that silently breaks two-device readers.
 */
@Document(collection = "changeLog")
@CompoundIndexes({
		@CompoundIndex(name = "reader_sequence", def = "{'userId': 1, 'sequence': 1}", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeLogEntry {

	@Id
	private String id;

	/** Whose feed this belongs to. The only field the feed query filters on. */
	private String userId;

	/** Monotonic per reader, allocated at write time. Starts at 1, so 0 is never a real entry. */
	private long sequence;

	/**
	 * From {@code library.api} deliberately — the same enum the loan, hold and reading modules pass
	 * in, so there is one vocabulary between the published port and the collection behind it.
	 */
	private ChangeReason reason;

	/** Always present: every reason is about a title. */
	private String itemId;

	/** Present for the loan reasons only. */
	private String loanId;

	/** Present for the hold reasons only. */
	private String holdId;

	/**
	 * When the change happened, to whole seconds. Reported to the app for display, and never used
	 * for ordering — that is what {@link #sequence} is for.
	 *
	 * <p>There is deliberately no separate {@code recordedAt} (write time): the client needs only
	 * this business time for display and {@link #sequence} for ordering, and replay diagnostics
	 * already have the outbox's {@code failedAt}/{@code attempts} plus the logs. A stored field that
	 * nothing reads would be weight without a reader.
	 */
	private Instant occurredAt;

}
