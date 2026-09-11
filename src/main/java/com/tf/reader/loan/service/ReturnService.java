package com.tf.reader.loan.service;

import java.time.Clock;
import java.time.Instant;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.hold.api.HoldPromotion;
import com.tf.reader.library.api.ChangeLog;
import com.tf.reader.library.api.ChangeReason;
import com.tf.reader.library.api.ChangeRecord;
import com.tf.reader.loan.dto.ReturnResponse;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;
import com.tf.reader.reading.api.CopyLease;

/**
 * Termination — closing a loan on the user's return (D-022).
 *
 * <p>The write order is the correctness (invariant #1): mark the loan {@code RETURNED} <em>first</em>,
 * then release the copy, then promote. Reverse it and there is a window where the slot is free while
 * the loan still reads active — one copy lent twice.
 *
 * <p>The lease and promotion are other modules' ports ({@code reading.CopyLease},
 * {@code hold.HoldPromotion}); we only call them.
 */
@Slf4j
@Service
public class ReturnService {

	private final LoanRepository loans;
	private final CopyLease copyLease;
	private final HoldPromotion holdPromotion;
	private final ChangeLog changeLog;
	private final Clock clock;

	// Five collaborators (repo + two ports + change-feed port + clock) — above the 3-param guideline,
	// but each is a distinct capability this one job genuinely needs; splitting would scatter the order.
	public ReturnService(LoanRepository loans, CopyLease copyLease, HoldPromotion holdPromotion,
			ChangeLog changeLog, Clock clock) {
		this.loans = loans;
		this.copyLease = copyLease;
		this.holdPromotion = holdPromotion;
		this.changeLog = changeLog;
		this.clock = clock;
	}

	/**
	 * @throws ApiException 404 if no such loan, 403 if it is not the caller's, 409 if it is already
	 *         closed (which makes a double-tapped return a safe no-op without an idempotency store).
	 */
	public ReturnResponse returnLoan(String userId, String loanId) {
		log.info("return: request loanId={} userId={}", loanId, userId);
		Loan loan = loans.findById(loanId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such loan."));
		if (!loan.getUserId().equals(userId)) {
			log.warn("return: forbidden loanId={} requestedBy={} actualOwner={}", loanId, userId, loan.getUserId());
			throw new ApiException(ErrorCode.FORBIDDEN_SCOPE, "This loan is not yours.");
		}
		if (loan.getStatus() != LoanStatus.ACTIVE) {
			log.info("return: already closed loanId={} userId={} status={}", loanId, userId, loan.getStatus());
			throw new ApiException(ErrorCode.LOAN_NOT_ACTIVE, "This loan is already closed.");
		}

		Instant now = clock.instant();
		loan.setStatus(LoanStatus.RETURNED);
		loan.setReturnedAt(now);
		Loan closed = loans.save(loan);
		log.info("return: revoked loanId={} itemId={} userId={} institutionId={}", closed.getLoanId(),
				closed.getItemId(), closed.getUserId(), closed.getInstitutionId());

		// After the state write, per the ChangeLog contract (D-029). It never throws, so no try/catch;
		// a feed miss is a delay, not a wrong answer, because GET /library reads the real loans.
		changeLog.record(ChangeRecord.forLoan(closed.getUserId(), ChangeReason.LOAN_RETURNED,
				closed.getItemId(), closed.getLoanId(), now));

		if (closed.getLeaseId() != null) {          // Elite only — release exactly once
			log.info("return: releasing copy lease loanId={} itemId={}", closed.getLoanId(), closed.getItemId());
			copyLease.release(closed.getLeaseId());
		}
		log.info("return: signalling a free slot loanId={} itemId={}", closed.getLoanId(), closed.getItemId());
		holdPromotion.promote(closed.getInstitutionId(), closed.getItemId());

		return new ReturnResponse(closed.getLoanId(), closed.getItemId(),
				closed.getStatus().name(), closed.getReturnedAt(), now);
	}
}
