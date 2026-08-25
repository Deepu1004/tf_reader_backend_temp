package com.tf.reader.hold.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Offer window, sweep interval, lease slack, lock TTL, reconcile interval —
// no magic numbers scattered through the service classes.
@Component
@ConfigurationProperties(prefix = "holds")
@Getter
@Setter
public class HoldProperties {

    private Duration offerWindow = Duration.ofMinutes(15);
    private Duration sweepInterval = Duration.ofSeconds(10);
    private Duration leaseSlack = Duration.ofSeconds(60);
    private Duration promoteLockTtl = Duration.ofSeconds(5);
    private Duration reconcileInterval = Duration.ofMinutes(5);

    @PostConstruct
    void validate() {
        // Correctness, not taste — a test, not a comment. Shorter than
        // 2x the sweep interval and a copy can read as free while somebody's
        // turn is still running: the sweep might not have caught up yet.
        if (leaseSlack.compareTo(sweepInterval.multipliedBy(2)) < 0) {
            throw new IllegalStateException(
                    "holds.lease-slack (" + leaseSlack + ") must be >= 2x holds.sweep-interval (" + sweepInterval + ")");
        }
        if (promoteLockTtl.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalStateException(
                    "holds.promote-lock-ttl (" + promoteLockTtl + ") is too long — a stuck lock freezes that title's queue");
        }
    }
}
