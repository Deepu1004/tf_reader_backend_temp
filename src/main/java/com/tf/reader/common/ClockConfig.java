package com.tf.reader.common;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One injected Clock for the whole backend.
 *
 * <p>Application code never calls {@code Instant.now()}. Every service takes this bean, so
 * a test can move time instead of sleeping — which is what makes loan expiry, offer
 * windows and lease TTLs testable at all.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
