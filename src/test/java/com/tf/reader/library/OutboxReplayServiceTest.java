package com.tf.reader.library;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.tf.reader.library.api.ChangeLog;
import com.tf.reader.library.api.ChangeReason;
import com.tf.reader.library.entity.OutboxEntry;
import com.tf.reader.library.repository.ChangeLogOutboxRepository;
import com.tf.reader.library.service.OutboxReplayService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxReplayServiceTest {

	private final ChangeLogOutboxRepository outbox = mock(ChangeLogOutboxRepository.class);
	private final ChangeLog changeLog = mock(ChangeLog.class);
	private final OutboxReplayService replay = new OutboxReplayService(outbox, changeLog);

	@Test
	@DisplayName("an entry that still fails to replay is kept in the outbox with its attempt count bumped")
	void stillFailingEntryIsKeptAndCounted() {
		OutboxEntry entry = OutboxEntry.builder()
				.userId("usr_x").reason(ChangeReason.LOAN_RETURNED).itemId("item_1").loanId("loan_1")
				.occurredAt(Instant.parse("2026-08-24T14:30:05Z"))
				.failedAt(Instant.parse("2026-08-24T14:30:06Z"))
				.attempts(2)
				.build();
		when(outbox.findAllByOrderByFailedAtAsc(any())).thenReturn(List.of(entry));
		// 0 is "not recorded" — the write failed again.
		when(changeLog.record(any())).thenReturn(0L);

		replay.replayAll();

		// Never deleted: dropping it would mean a change that never reaches the feed.
		verify(outbox, never()).deleteById(any());
		// Kept, with attempts advanced for visibility into a jammed entry.
		ArgumentCaptor<OutboxEntry> saved = ArgumentCaptor.forClass(OutboxEntry.class);
		verify(outbox).save(saved.capture());
		assertThat(saved.getValue().getAttempts()).isEqualTo(3);
	}
}
