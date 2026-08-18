package com.tf.reader.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.loan.entity.LicenseModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;

/**
 * Proves the two invariants the {@code loans} collection enforces in the store rather than in
 * application code (D-003, D-017): the partial-unique index scoped to {@code status:ACTIVE}, and
 * the finders the create flow and expiry sweeper rely on.
 *
 * <p>Runs against a real Mongo (Testcontainers) with {@code auto-index-creation: true}, so these
 * assertions exercise the actual index behaviour — the only thing that can tell us the
 * {@code partialFilter} syntax is right.
 */
@Import(TestcontainersConfiguration.class)
@DataMongoTest
class LoanRepositoryTest {

	@Autowired
	private LoanRepository loans;

	@BeforeEach
	void clean() {
		loans.deleteAll();
	}

	@Test
	void rejectsSecondActiveLoanForSameUserAndItem() {
		loans.save(activeLoan("loan_1", "user_1", "item_1"));

		assertThatThrownBy(() -> loans.save(activeLoan("loan_2", "user_1", "item_1")))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void allowsNewActiveLoanOnceThePreviousOneIsReturned() {
		// The partial filter scopes uniqueness to ACTIVE only: a RETURNED loan must not block a re-borrow.
		Loan returned = activeLoan("loan_1", "user_1", "item_1");
		returned.setStatus(LoanStatus.RETURNED);
		returned.setReturnedAt(Instant.now());
		loans.save(returned);

		Loan reborrow = loans.save(activeLoan("loan_2", "user_1", "item_1"));

		assertThat(reborrow.getLoanId()).isEqualTo("loan_2");
		assertThat(loans.count()).isEqualTo(2);
	}

	@Test
	void allowsSameItemActiveForDifferentUsers() {
		loans.save(activeLoan("loan_1", "user_1", "item_1"));
		loans.save(activeLoan("loan_2", "user_2", "item_1"));

		assertThat(loans.count()).isEqualTo(2);
	}

	@Test
	void findsTheActiveLoanForAUserAndItem() {
		loans.save(activeLoan("loan_1", "user_1", "item_1"));

		Optional<Loan> found = loans.findByUserIdAndItemIdAndStatus("user_1", "item_1", LoanStatus.ACTIVE);

		assertThat(found).isPresent();
		assertThat(found.get().getLoanId()).isEqualTo("loan_1");
	}

	@Test
	void sweeperFindsOnlyPastDueActiveLoansAndSkipsOpenEndedOnes() {
		Instant now = Instant.now();

		Loan pastDue = activeLoan("loan_pastdue", "user_1", "item_1");
		pastDue.setDueAt(now.minus(1, ChronoUnit.HOURS));
		loans.save(pastDue);

		Loan future = activeLoan("loan_future", "user_2", "item_2");
		future.setDueAt(now.plus(1, ChronoUnit.HOURS));
		loans.save(future);

		Loan openEnded = activeLoan("loan_open", "user_3", "item_3");
		openEnded.setDueAt(null); // Subscription/Open-Access — must never be swept (D-005)
		loans.save(openEnded);

		Loan alreadyExpired = activeLoan("loan_expired", "user_4", "item_4");
		alreadyExpired.setStatus(LoanStatus.EXPIRED);
		alreadyExpired.setDueAt(now.minus(2, ChronoUnit.HOURS));
		loans.save(alreadyExpired);

		List<Loan> due = loans.findByStatusAndDueAtLessThanEqual(LoanStatus.ACTIVE, now);

		assertThat(due).extracting(Loan::getLoanId).containsExactly("loan_pastdue");
	}

	private static Loan activeLoan(String loanId, String userId, String itemId) {
		return Loan.builder()
				.loanId(loanId)
				.userId(userId)
				.itemId(itemId)
				.licenseModel(LicenseModel.ELITE)
				.status(LoanStatus.ACTIVE)
				.canPersist(false)
				.borrowedAt(Instant.now())
				.dueAt(Instant.now().plus(14, ChronoUnit.DAYS))
				.build();
	}
}
