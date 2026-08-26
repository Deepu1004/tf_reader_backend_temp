package com.tf.reader.loan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.repository.LoanRepository;
import com.tf.reader.loan.service.LoanSeedRunner;

/**
 * The demo loan seeder (D-028). On an empty local DB it inserts one loan per tier plus a returned
 * one for the dev-token reader; on a DB that already has them it does nothing (idempotent).
 */
class LoanSeedRunnerTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC);

	private final LoanRepository loans = mock(LoanRepository.class);
	private final LoanSeedRunner runner = new LoanSeedRunner(loans, CLOCK);

	@Test
	void seedsTheDemoLoansWhenNoneExistYet() throws Exception {
		when(loans.existsById(LoanSeedRunner.SENTINEL_LOAN_ID)).thenReturn(false);

		runner.run(null);

		// One ACTIVE per tier (SUBSCRIPTION, ELITE, OPEN_ACCESS) plus one RETURNED = 4 loans.
		verify(loans, times(4)).save(any(Loan.class));
	}

	@Test
	void doesNothingWhenTheDemoLoansAlreadyExist() throws Exception {
		when(loans.existsById(LoanSeedRunner.SENTINEL_LOAN_ID)).thenReturn(true);

		runner.run(null);

		verify(loans, never()).save(any(Loan.class));
	}
}
