package com.tf.reader.library.service;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.tf.reader.library.api.ChangeLog;
import com.tf.reader.library.api.ChangeRecord;
import com.tf.reader.library.entity.OutboxEntry;
import com.tf.reader.library.repository.ChangeLogOutboxRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Replays change-log entries that failed to write the first time, oldest first.
 *
 * <p>Goes back through the published {@link ChangeLog} port, not the repository directly — a
 * successful replay needs a real allocated sequence, and only {@code ChangeLog.record()} knows
 * how to get one. A replayed entry lands with today's sequence, not the one it would have had
 * originally; per {@link ChangeLog}'s own contract this is a delay, never a wrong answer, and a
 * duplicate on a genuinely ambiguous failure (the write may have actually succeeded before the
 * exception surfaced) is explicitly tolerated there for the same reason.
 */
@Slf4j
@Service
public class OutboxReplayService {

	/**
	 * How many entries one tick will attempt.
	 *
	 * <p>A backlog drains over several ticks rather than in one pass, which is the point: the run
	 * after a long outage is the one with the most to do and the least headroom to do it in, since
	 * the database it is hammering has only just come back. Draining steadily gets the same entries
	 * written without making recovery the heaviest minute of the day.
	 */
	private static final int BATCH = 200;

	private final ChangeLogOutboxRepository outbox;
	private final ChangeLog changeLog;

	public OutboxReplayService(ChangeLogOutboxRepository outbox, ChangeLog changeLog) {
		this.outbox = outbox;
		this.changeLog = changeLog;
	}

	@Scheduled(fixedDelayString = "${library.outbox.replay-interval-ms:60000}")
	public void replayAll() {
		List<OutboxEntry> batch = outbox.findAllByOrderByFailedAtAsc(Limit.of(BATCH));
		for (OutboxEntry entry : batch) {
			replay(entry);
		}
		if (batch.size() == BATCH) {
			// Said out loud, because a silently truncated pass looks identical to a drained one and
			// this is the state worth noticing: the outbox is filling faster than it empties.
			log.warn("change log outbox replay hit its batch limit of {}; more entries remain", BATCH);
		}
	}

	private void replay(OutboxEntry entry) {
		ChangeRecord change = new ChangeRecord(
				entry.getUserId(), entry.getReason(), entry.getItemId(),
				entry.getLoanId(), entry.getHoldId(), entry.getOccurredAt());

		long sequence = changeLog.record(change);
		if (sequence != 0L) {
			outbox.deleteById(entry.getId());
			return;
		}

		// Still failing. Left in place for the next run rather than deleted or retried in a
		// loop here — attempts is bumped only for visibility into a jammed entry, not to cap
		// retries; nothing here decides an entry is unrecoverable.
		entry.setAttempts(entry.getAttempts() + 1);
		outbox.save(entry);
		log.warn("change log replay still failing, reader={} reason={} item={} attempts={}",
				entry.getUserId(), entry.getReason(), entry.getItemId(), entry.getAttempts());
	}
}
