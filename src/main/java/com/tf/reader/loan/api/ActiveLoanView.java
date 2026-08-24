package com.tf.reader.loan.api;

import java.time.Instant;

/**
 * Read model of a currently held, still-live loan.
 *
 * <p>Returned only when the loan is genuinely active — see {@link ActiveLoanQuery}, which
 * re-derives liveness from {@code dueAt} rather than trusting the stored status (D-006).
 *
 * @param dueAt      scheduled end; {@code null} for an open-ended (Subscription / Open-Access) loan.
 * @param borrowedAt when the loan was created, on the server clock. Needed by the library screen
 *                   for time-remaining countdowns (D-026).
 * @param status     lifecycle state as a string — always {@code "ACTIVE"} from this port since
 *                   D-006 liveness is re-derived here, but carried explicitly so callers (Module E)
 *                   do not hard-code a string constant (D-026).
 */
public record ActiveLoanView(
		String loanId,
		String itemId,
		String licenceModel,
		boolean canPersist,
		Instant dueAt,
		Instant borrowedAt,
		String status) {
}
