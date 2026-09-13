package com.tf.reader.hold;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's scheduler so the hold module's {@code OfferSweeper} and
 * {@code QueueReconciler} run.
 *
 * <p>Kept in the hold module rather than annotating {@code ReaderApplication}, so enabling
 * scheduling is owned by the capability that needs it and does not touch the shared entry point
 * — same reasoning as {@code LoanSchedulingConfig}, {@code IngestSchedulingConfig} and
 * {@code LibrarySchedulingConfig}. {@code OfferSweeper} has been running on the strength of one
 * of those other modules' {@code @EnableScheduling} alone; hold never declared its own.
 */
@Configuration
@EnableScheduling
public class HoldSchedulingConfig {
}
