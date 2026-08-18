package com.tf.reader.loan.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.loan.api.LicenseCommand;
import com.tf.reader.loan.api.LicenseView;
import com.tf.reader.loan.entity.LicenseModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;

/**
 * Creates loan records on behalf of the reading module, which has already
 * checked entitlement and acquired a lease before calling here.
 *
 * <p>Idempotent: if an ACTIVE loan already exists for (userId, itemId), it
 * is returned as-is — a double-tap on "open book" must not consume a second slot.
 */
@Service
class LicenseCommandImpl implements LicenseCommand {

	private final LoanRepository loans;
	private final Clock clock;

	LicenseCommandImpl(LoanRepository loans, Clock clock) {
		this.loans = loans;
		this.clock = clock;
	}

	@Override
	public LicenseView create(SubjectRef subject, String itemId, AccessLevel accessLevel,
			int loanPeriodDays, String leaseId) {

		return loans.findByUserIdAndItemIdAndStatus(subject.userId(), itemId, LoanStatus.ACTIVE)
				.map(this::toView)
				.orElseGet(() -> createNew(subject, itemId, accessLevel, loanPeriodDays, leaseId));
	}

	private LicenseView createNew(SubjectRef subject, String itemId, AccessLevel accessLevel,
			int loanPeriodDays, String leaseId) {

		LicenseModel model = toLicenseModel(accessLevel);
		Instant now = Instant.now(clock);

		Loan loan = Loan.builder()
				.loanId("loan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
				.userId(subject.userId())
				.itemId(itemId)
				.institutionId(subject.institutionId())
				.licenseModel(model)
				.status(LoanStatus.ACTIVE)
				.canPersist(model != LicenseModel.ELITE)
				.leaseId(leaseId)
				.borrowedAt(now)
				.dueAt(loanPeriodDays > 0 ? now.plus(loanPeriodDays, ChronoUnit.DAYS) : null)
				.build();

		try {
			return toView(loans.save(loan));
		} catch (DuplicateKeyException e) {
			// Race — another request won the insert; return the winner
			return loans.findByUserIdAndItemIdAndStatus(subject.userId(), itemId, LoanStatus.ACTIVE)
					.map(this::toView)
					.orElseThrow(() -> new IllegalStateException("Race on loan create could not be resolved"));
		}
	}

	private LicenseView toView(Loan loan) {
		return new LicenseView(
				loan.getLoanId(),
				loan.getUserId(),
				loan.getItemId(),
				toAccessLevel(loan.getLicenseModel()),
				loan.isCanPersist(),
				loan.getDueAt(),
				loan.getLeaseId());
	}

	private LicenseModel toLicenseModel(AccessLevel level) {
		return switch (level) {
			case OPEN_ACCESS -> LicenseModel.OPEN_ACCESS;
			case ENTITLED_UNLIMITED -> LicenseModel.SUBSCRIPTION;
			case ENTITLED_CONCURRENT -> LicenseModel.ELITE;
		};
	}

	private AccessLevel toAccessLevel(LicenseModel model) {
		return switch (model) {
			case OPEN_ACCESS -> AccessLevel.OPEN_ACCESS;
			case SUBSCRIPTION -> AccessLevel.ENTITLED_UNLIMITED;
			case ELITE -> AccessLevel.ENTITLED_CONCURRENT;
		};
	}
}
