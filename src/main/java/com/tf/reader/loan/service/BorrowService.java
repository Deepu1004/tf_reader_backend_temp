package com.tf.reader.loan.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.loan.entity.LicenseModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;
import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;

@Service
public class BorrowService {

	private static final Logger log = LoggerFactory.getLogger(BorrowService.class);

	private final LoanRepository loans;
	private final EntitlementQuery entitlementQuery;
	private final CopyLease copyLease;
	private final Clock clock;

	public BorrowService(LoanRepository loans, EntitlementQuery entitlementQuery,
			CopyLease copyLease, Clock clock) {
		this.loans = loans;
		this.entitlementQuery = entitlementQuery;
		this.copyLease = copyLease;
		this.clock = clock;
	}

	/**
	 * @return a result carrying the loan and whether it was newly created.
	 *         The caller maps {@code created=false} → HTTP 200, {@code created=true} → HTTP 201.
	 */
	public BorrowResult borrow(String userId, String institutionId, String itemId) {

		// 1. Entitlement first — nothing written if denied (D-009)
		EntitlementDecision decision = entitlementQuery.check(new SubjectRef(userId, institutionId), itemId);
		if (!decision.entitled()) {
			throw new ApiException(ErrorCode.NO_ENTITLEMENT,
					"Not entitled to access this title.");
		}

		// 2. Duplicate check BEFORE any Redis/lease call (invariant #2)
		Optional<Loan> existing = loans.findByUserIdAndItemIdAndStatus(userId, itemId, LoanStatus.ACTIVE);
		if (existing.isPresent()) {
			return new BorrowResult(existing.get(), false);
		}

		// 3. AccessLevel → LicenseModel in one place (D-009)
		LicenseModel model = toLicenseModel(decision.accessLevel());

		// 4. Acquire lease for Elite only — never for Subscription or Open Access
		Optional<LeaseHandle> lease = Optional.empty();
		if (model == LicenseModel.ELITE) {
			lease = copyLease.acquire(itemId);
			if (lease.isEmpty()) {
				throw new ApiException(ErrorCode.NO_COPIES_AVAILABLE,
						"No copies available. Join the queue at /api/v1/holds.");
			}
		}

		// 5. Build and save — if the save fails, the lease must be released exactly once
		Instant now = Instant.now(clock);
		Loan loan = Loan.builder()
				.loanId("loan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
				.userId(userId)
				.itemId(itemId)
				.institutionId(institutionId)
				.licenseModel(model)
				.status(LoanStatus.ACTIVE)
				.canPersist(model != LicenseModel.ELITE)
				.borrowedAt(now)
				.dueAt(dueAt(model, decision, now))
				.build();

		try {
			Loan saved = loans.save(loan);
			return new BorrowResult(saved, true);
		} catch (DuplicateKeyException e) {
			// Race: another request won the insert between our duplicate check and our save.
			// Release our lease (if Elite) and return the winner's loan — 200, not 500 (D-003).
			lease.ifPresent(h -> copyLease.release(h.leaseId()));
			return loans.findByUserIdAndItemIdAndStatus(userId, itemId, LoanStatus.ACTIVE)
					.map(winner -> new BorrowResult(winner, false))
					.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
							"Loan race could not be resolved."));
		} catch (Exception e) {
			// Any other save failure — release the lease so the slot is not stranded (invariant #1)
			lease.ifPresent(h -> copyLease.release(h.leaseId()));
			log.error("Failed to save loan for user={} item={}", userId, itemId, e);
			throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to create loan.");
		}
	}

	// D-009: one translation point for the two vocabularies
	private LicenseModel toLicenseModel(AccessLevel level) {
		return switch (level) {
			case OPEN_ACCESS -> LicenseModel.OPEN_ACCESS;
			case ENTITLED_UNLIMITED -> LicenseModel.SUBSCRIPTION;
			case ENTITLED_CONCURRENT -> LicenseModel.ELITE;
		};
	}

	private Instant dueAt(LicenseModel model, EntitlementDecision decision, Instant now) {
		return switch (model) {
			// Open access: never expires, no slot consumed
			case OPEN_ACCESS -> null;
			// Subscription: open-ended when loanPeriodDays is 0; otherwise set from entitlement
			case SUBSCRIPTION -> decision.loanPeriodDays() > 0
					? now.plus(decision.loanPeriodDays(), ChronoUnit.DAYS)
					: null;
			// Elite: always set — the slot has a cost, and the sweeper must reclaim it
			case ELITE -> now.plus(decision.loanPeriodDays(), ChronoUnit.DAYS);
		};
	}

	/** Carrier for the borrow result so the controller can pick the right HTTP status. */
	public record BorrowResult(Loan loan, boolean created) {
	}
}

