package com.tf.reader.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tf.reader.loan.api.ActiveLoanView;
import com.tf.reader.loan.entity.LicenceModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;
import com.tf.reader.loan.service.ActiveLoanQueryImpl;

/**
 * The active-licence check — both point query ({@code findActive}) and shelf query
 * ({@code findAllFor}, D-025). The same D-006 liveness rule applies to both: a loan is live iff it
 * is {@code ACTIVE} AND ({@code dueAt} is null OR still in the future on the server clock). A
 * lapsed-but-not-yet-swept row must not appear on a reader's point check or their shelf.
 */
class ActiveLoanQueryTest {

	private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final LoanRepository loans = mock(LoanRepository.class);
	private final ActiveLoanQueryImpl query = new ActiveLoanQueryImpl(loans, CLOCK);

	@Test
	void reportsActiveWhenTheLoanIsStillWithinItsDueDate() {
		stubActiveRow(loanDueAt(NOW.plus(Duration.ofDays(3))));

		Optional<ActiveLoanView> result = query.findActive("user_1", "item_1");

		assertThat(result).isPresent();
		assertThat(result.get().loanId()).isEqualTo("loan_1");
	}

	@Test
	void treatsAnOpenEndedLoanAsAlwaysActive() {
		stubActiveRow(loanDueAt(null));

		assertThat(query.findActive("user_1", "item_1")).isPresent();
	}

	@Test
	void reDerivesInactiveWhenTheDueDateHasAlreadyPassed() {
		// The row still says ACTIVE — the sweeper has not run yet. D-006: we do not trust it.
		stubActiveRow(loanDueAt(NOW.minus(Duration.ofSeconds(1))));

		assertThat(query.findActive("user_1", "item_1")).isEmpty();
	}

	@Test
	void reportsInactiveWhenThereIsNoActiveRowAtAll() {
		when(loans.findByUserIdAndItemIdAndStatus("user_1", "item_1", LoanStatus.ACTIVE))
				.thenReturn(Optional.empty());

		assertThat(query.findActive("user_1", "item_1")).isEmpty();
	}

	// ── findAllFor ──────────────────────────────────────────────────────────────────────────────

	@Test
	void findAllForReturnsOnlyLiveLoansSortedNewestFirst() {
		Loan live1 = loan("loan_a", "item_a", NOW.minus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(5)));
		Loan live2 = loan("loan_b", "item_b", NOW.minus(Duration.ofDays(3)), null); // open-ended
		Loan lapsed = loan("loan_c", "item_c", NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofSeconds(1)));
		when(loans.findByUserIdAndStatus("user_1", LoanStatus.ACTIVE))
				.thenReturn(List.of(live1, live2, lapsed));

		List<ActiveLoanView> result = query.findAllFor("user_1");

		assertThat(result).hasSize(2);
		assertThat(result.get(0).loanId()).isEqualTo("loan_a"); // newest borrowedAt first
		assertThat(result.get(1).loanId()).isEqualTo("loan_b");
	}

	@Test
	void findAllForReturnsEmptyWhenUserHasNoLoans() {
		when(loans.findByUserIdAndStatus("user_1", LoanStatus.ACTIVE)).thenReturn(List.of());

		assertThat(query.findAllFor("user_1")).isEmpty();
	}

	@Test
	void findAllForExcludesLapsedLoansEvenIfStatusIsStillActive() {
		Loan lapsed = loan("loan_x", "item_x", NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1)));
		when(loans.findByUserIdAndStatus("user_1", LoanStatus.ACTIVE)).thenReturn(List.of(lapsed));

		assertThat(query.findAllFor("user_1")).isEmpty();
	}

	private void stubActiveRow(Loan loan) {
		when(loans.findByUserIdAndItemIdAndStatus("user_1", "item_1", LoanStatus.ACTIVE))
				.thenReturn(Optional.of(loan));
	}

	private Loan loanDueAt(Instant dueAt) {
		return Loan.builder()
				.loanId("loan_1").userId("user_1").itemId("item_1")
				.licenceModel(LicenceModel.ELITE).status(LoanStatus.ACTIVE)
				.canPersist(false).borrowedAt(NOW.minus(Duration.ofDays(1))).dueAt(dueAt)
				.build();
	}

	private Loan loan(String loanId, String itemId, Instant borrowedAt, Instant dueAt) {
		return Loan.builder()
				.loanId(loanId).userId("user_1").itemId(itemId)
				.licenceModel(LicenceModel.SUBSCRIPTION).status(LoanStatus.ACTIVE)
				.canPersist(true).borrowedAt(borrowedAt).dueAt(dueAt)
				.build();
	}
}
