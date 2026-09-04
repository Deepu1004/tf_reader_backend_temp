package com.tf.reader.library;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler so {@code OutboxReplayService} drains the change-log outbox.
 *
 * <p>Redundant with {@code loan.LoanSchedulingConfig} and {@code ingest.IngestSchedulingConfig} at
 * runtime — scheduling is a context-wide Spring feature once enabled anywhere — but kept for the
 * same reason those two are kept: enabling it stays owned by the capability that needs it, and it
 * stays correct if either of the others is removed or profile-gated.
 *
 * <p>That is not hypothetical here. Without this file the outbox replay runs only because another
 * module happens to enable scheduling, and if that changed the replay would stop <em>silently</em> —
 * no failure, no log, just change-feed entries that failed once and are never retried. The reader's
 * device would go on believing a returned book is still theirs.
 */
@Configuration
@EnableScheduling
public class LibrarySchedulingConfig {
}
