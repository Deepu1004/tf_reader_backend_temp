package com.tf.reader.ingest;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler so {@code IngestProcessor}'s queue drain and watchdog run.
 *
 * <p>Redundant with {@code loan.LoanSchedulingConfig} at runtime - scheduling is a context-wide
 * Spring feature once enabled anywhere - but kept anyway so enabling it stays owned by the module
 * that needs it, matching that precedent, and stays correct if loan's config is ever removed.
 */
@Configuration
@EnableScheduling
public class IngestSchedulingConfig {
}
