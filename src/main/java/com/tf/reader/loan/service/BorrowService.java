package com.tf.reader.loan.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.loan.api.LicenceCommand;
import com.tf.reader.loan.api.LicenceView;
import com.tf.reader.loan.entity.LicenceModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;
import org.springframework.stereotype.Service;

@Service
public class BorrowService implements LicenceCommand {

	private final LoanRepository loanRepository;
	private final Clock clock;

	public BorrowService(LoanRepository loanRepository, Clock clock) {
		this.loanRepository = loanRepository;
		this.clock = clock;
	}

	@Override
	public LicenceView create(SubjectRef subject, String itemId, AccessLevel accessLevel, int loanPeriodDays, String leaseId) {
		String userId = subject != null ? subject.userId() : null;
		String institutionId = subject != null ? subject.institutionId() : null;

		if (userId != null) {
			var existing = loanRepository.findByUserIdAndItemIdAndStatus(userId, itemId, LoanStatus.ACTIVE);
			if (existing.isPresent()) {
				Loan loan = existing.get();
				return new LicenceView(
						loan.getLoanId(),
						loan.getUserId(),
						loan.getItemId(),
						accessLevel,
						loan.isCanPersist(),
						loan.getDueAt(),
						loan.getLeaseId()
				);
			}
		}

		LicenceModel model = switch (accessLevel) {
			case OPEN_ACCESS -> LicenceModel.OPEN_ACCESS;
			case ENTITLED_UNLIMITED -> LicenceModel.SUBSCRIPTION;
			case ENTITLED_CONCURRENT -> LicenceModel.ELITE;
		};

		boolean canPersist = (accessLevel != AccessLevel.ENTITLED_CONCURRENT);
		Instant now = clock.instant();
		Instant dueAt = (accessLevel == AccessLevel.ENTITLED_CONCURRENT && loanPeriodDays > 0)
				? now.plus(java.time.Duration.ofDays(loanPeriodDays))
				: null;

		Loan loan = Loan.builder()
				.loanId("loan_" + UUID.randomUUID().toString().substring(0, 8))
				.itemId(itemId)
				.userId(userId)
				.institutionId(institutionId)
				.licenceModel(model)
				.status(LoanStatus.ACTIVE)
				.canPersist(canPersist)
				.leaseId(leaseId)
				.borrowedAt(now)
				.dueAt(dueAt)
				.build();

		try {
			loan = loanRepository.save(loan);
		} catch (Exception e) {
			if (userId != null) {
				var existing = loanRepository.findByUserIdAndItemIdAndStatus(userId, itemId, LoanStatus.ACTIVE);
				if (existing.isPresent()) {
					Loan existingLoan = existing.get();
					return new LicenceView(
							existingLoan.getLoanId(),
							existingLoan.getUserId(),
							existingLoan.getItemId(),
							accessLevel,
							existingLoan.isCanPersist(),
							existingLoan.getDueAt(),
							existingLoan.getLeaseId()
					);
				}
			}
			throw e;
		}

		return new LicenceView(
				loan.getLoanId(),
				loan.getUserId(),
				loan.getItemId(),
				accessLevel,
				loan.isCanPersist(),
				loan.getDueAt(),
				loan.getLeaseId()
		);
	}
}

