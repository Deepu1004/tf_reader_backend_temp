package com.tf.reader.loan.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.tf.reader.hold.api.HoldPromotion;
import com.tf.reader.library.api.ChangeLog;
import com.tf.reader.library.api.ChangeReason;
import com.tf.reader.library.api.ChangeRecord;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;
import com.tf.reader.reading.api.CopyLease;

/**
 * Termination without a user — closes loans whose clock has run out (D-023).
 *
 * <p>Redis expiry deletes a key but runs no code, so the copy count would silently drift; this
 * Mongo-driven sweep is what actually reclaims the slot. It uses the same order as return
 * (mark {@code EXPIRED} → release the copy → promote) and is best-effort per item: one failure is
 * logged and the batch continues. Open-ended loans ({@code dueAt == null}) never match the finder,
 * so they are skipped for free.
 */
@Service
public class ExpirySweeper {

	private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);

	private final LoanRepository loans;
	private final CopyLease copyLease;
	private final HoldPromotion holdPromotion;
	private final ChangeLog changeLog;
	private final Clock clock;

	// Five collaborators (repo + two ports + change-feed port + clock) — the change-feed write is the
	// only addition to the same order return uses (D-029).
	public ExpirySweeper(LoanRepository loans, CopyLease copyLease, HoldPromotion holdPromotion,
			ChangeLog changeLog, Clock clock) {
		this.loans = loans;
		this.copyLease = copyLease;
		this.holdPromotion = holdPromotion;
		this.changeLog = changeLog;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${loan.expiry-sweep.interval-ms:60000}")
	public void sweep() {
		Instant now = clock.instant();
		List<Loan> due = loans.findByStatusAndDueAtLessThanEqual(LoanStatus.ACTIVE, now);
		for (Loan loan : due) {
			try {
				expire(loan, now);
			} catch (RuntimeException e) {
				// Best-effort: a single bad row must not strand every later slot (D-023).
				log.error("Expiry sweep failed for loan {} item {}", loan.getLoanId(), loan.getItemId(), e);
			}
		}
	}

	private void expire(Loan loan, Instant now) {
		loan.setStatus(LoanStatus.EXPIRED);
		loan.setExpiredAt(now);
		Loan closed = loans.save(loan);
		// After the state write, per the ChangeLog contract (D-029). One entry per expired loan; the
		// port never throws, and the per-item try/catch in sweep() already isolates any failure.
		changeLog.record(ChangeRecord.forLoan(closed.getUserId(), ChangeReason.LOAN_EXPIRED,
				closed.getItemId(), closed.getLoanId(), now));
		if (closed.getLeaseId() != null) {          // Elite only — release exactly once
			copyLease.release(closed.getLeaseId());
		}
		holdPromotion.promote(closed.getInstitutionId(), closed.getItemId());
	}
}
