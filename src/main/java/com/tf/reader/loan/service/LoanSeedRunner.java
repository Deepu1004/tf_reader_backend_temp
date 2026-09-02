package com.tf.reader.loan.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.tf.reader.loan.entity.LicenceModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;

/**
 * Seeds a handful of demo loans on a fresh local database (D-028), so the dev-token reader has a
 * populated library from the first sign-in and DoD #6 ("works from an empty database") holds.
 *
 * <p>Gated exactly like {@code DemoDataSeeder} — local profile plus {@code tnf.seed.enabled=true} —
 * and ordered to run after it, so the items these loans reference already exist. Idempotent: if the
 * sentinel loan is present, it does nothing, so a restart never duplicates rows.
 *
 * <p>Loans are inserted directly, matching how {@code DemoDataSeeder} inserts its fixtures — a seed
 * is a fixture, not a borrow, so it stays independent of the entitlement port and the copy lease.
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "tnf.seed", name = "enabled", havingValue = "true")
@Order(100) // after DemoDataSeeder (default order) so the referenced items are seeded first
public class LoanSeedRunner implements ApplicationRunner {

	/** Presence of this loan means the demo set is already seeded — the idempotency guard. */
	public static final String SENTINEL_LOAN_ID = "loan_seed_sub";

	private static final Logger log = LoggerFactory.getLogger(LoanSeedRunner.class);

	private static final String DEMO_USER = "usr_dev123";
	private static final String DEMO_INSTITUTION = "inst_7f3";

	private final LoanRepository loans;
	private final Clock clock;

	public LoanSeedRunner(LoanRepository loans, Clock clock) {
		this.loans = loans;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (loans.existsById(SENTINEL_LOAN_ID)) {
			return;
		}

		Instant now = clock.instant();

		// One ACTIVE per tier so every licence model shows on the library screen.
		loans.save(active(SENTINEL_LOAN_ID, "item_q7", LicenceModel.SUBSCRIPTION, true, null, now));
		loans.save(active("loan_seed_elite", "item_42", LicenceModel.ELITE, false,
				now.plus(Duration.ofDays(14)), now));
		loans.save(active("loan_seed_oa", "item_ab6", LicenceModel.OPEN_ACCESS, true, null, now));

		// One RETURNED so the ?status=RETURNED filter has something to show.
		loans.save(returned("loan_seed_returned", "item_stat", now));

		log.info("Seeded 4 demo loans for {}", DEMO_USER);
	}

	private Loan active(String loanId, String itemId, LicenceModel model, boolean canPersist,
			Instant dueAt, Instant now) {
		return Loan.builder()
				.loanId(loanId).userId(DEMO_USER).itemId(itemId).institutionId(DEMO_INSTITUTION)
				.licenceModel(model).status(LoanStatus.ACTIVE).canPersist(canPersist)
				.borrowedAt(now.minus(Duration.ofDays(1))).dueAt(dueAt)
				.build();
	}

	private Loan returned(String loanId, String itemId, Instant now) {
		return Loan.builder()
				.loanId(loanId).userId(DEMO_USER).itemId(itemId).institutionId(DEMO_INSTITUTION)
				.licenceModel(LicenceModel.SUBSCRIPTION).status(LoanStatus.RETURNED).canPersist(true)
				.borrowedAt(now.minus(Duration.ofDays(10))).returnedAt(now.minus(Duration.ofDays(2)))
				.build();
	}
}
