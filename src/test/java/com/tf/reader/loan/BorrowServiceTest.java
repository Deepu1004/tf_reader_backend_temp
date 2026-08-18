package com.tf.reader.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.loan.entity.LicenseModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;
import com.tf.reader.loan.repository.LoanRepository;
import com.tf.reader.loan.service.BorrowService;
import com.tf.reader.loan.service.BorrowService.BorrowResult;
import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;

@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

	@Mock LoanRepository loans;
	@Mock EntitlementQuery entitlementQuery;
	@Mock CopyLease copyLease;

	// Fixed clock so dueAt assertions are deterministic
	private static final Instant NOW = Instant.parse("2026-08-18T09:00:00Z");
	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private BorrowService service;

	@BeforeEach
	void setUp() {
		service = new BorrowService(loans, entitlementQuery, copyLease, clock);
	}

	// ── entitlement ───────────────────────────────────────────────────

	@Test
	void throwsNoEntitlementWhenNotEntitled() {
		when(entitlementQuery.check(any(), eq("item_1")))
				.thenReturn(denied());

		assertThatThrownBy(() -> service.borrow("user_1", "inst_1", "item_1"))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode())
						.isEqualTo(ErrorCode.NO_ENTITLEMENT));

		verify(loans, never()).save(any());
	}

	// ── duplicate check ───────────────────────────────────────────────

	@Test
	void returnsExistingLoanWithoutCreatingANew() {
		Loan existing = activeLoan("loan_existing", LicenseModel.SUBSCRIPTION);
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(subscription());
		when(loans.findByUserIdAndItemIdAndStatus("user_1", "item_1", LoanStatus.ACTIVE))
				.thenReturn(Optional.of(existing));

		BorrowResult result = service.borrow("user_1", "inst_1", "item_1");

		assertThat(result.created()).isFalse();
		assertThat(result.loan().getLoanId()).isEqualTo("loan_existing");
		verify(loans, never()).save(any());
		verify(copyLease, never()).acquire(any());
	}

	// ── OPEN_ACCESS path ──────────────────────────────────────────────

	@Test
	void createsOpenAccessLoanWithNoDueDateAndCanPersistTrue() {
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(openAccess());
		when(loans.findByUserIdAndItemIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
		when(loans.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BorrowResult result = service.borrow("user_1", "inst_1", "item_1");

		assertThat(result.created()).isTrue();
		assertThat(result.loan().getLicenseModel()).isEqualTo(LicenseModel.OPEN_ACCESS);
		assertThat(result.loan().getDueAt()).isNull();
		assertThat(result.loan().isCanPersist()).isTrue();
		verify(copyLease, never()).acquire(any());
	}

	// ── SUBSCRIPTION path ─────────────────────────────────────────────

	@Test
	void createsSubscriptionLoanWithDueDateFromEntitlement() {
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(subscription());
		when(loans.findByUserIdAndItemIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
		when(loans.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BorrowResult result = service.borrow("user_1", "inst_1", "item_1");

		assertThat(result.created()).isTrue();
		assertThat(result.loan().getLicenseModel()).isEqualTo(LicenseModel.SUBSCRIPTION);
		assertThat(result.loan().getDueAt()).isEqualTo(NOW.plus(14, java.time.temporal.ChronoUnit.DAYS));
		assertThat(result.loan().isCanPersist()).isTrue();
		verify(copyLease, never()).acquire(any());
	}

	// ── ELITE path ────────────────────────────────────────────────────

	@Test
	void createsEliteLoanAfterAcquiringLease() {
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(elite());
		when(loans.findByUserIdAndItemIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
		when(copyLease.acquire("item_1")).thenReturn(Optional.of(new LeaseHandle("lease_abc", "inst_1", "item_1", NOW.plusSeconds(3600))));
		when(loans.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BorrowResult result = service.borrow("user_1", "inst_1", "item_1");

		assertThat(result.created()).isTrue();
		assertThat(result.loan().getLicenseModel()).isEqualTo(LicenseModel.ELITE);
		assertThat(result.loan().isCanPersist()).isFalse();
		assertThat(result.loan().getDueAt()).isNotNull();
	}

	@Test
	void throwsNoCopiesAvailableWhenLeaseIsEmpty() {
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(elite());
		when(loans.findByUserIdAndItemIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
		when(copyLease.acquire("item_1")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.borrow("user_1", "inst_1", "item_1"))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode())
						.isEqualTo(ErrorCode.NO_COPIES_AVAILABLE));

		verify(loans, never()).save(any());
	}

	// ── save-fails → release lease ────────────────────────────────────

	@Test
	void releasesLeaseWhenSaveFails() {
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(elite());
		when(loans.findByUserIdAndItemIdAndStatus(any(), any(), any())).thenReturn(Optional.empty());
		when(copyLease.acquire("item_1")).thenReturn(Optional.of(new LeaseHandle("lease_abc", "inst_1", "item_1", NOW.plusSeconds(3600))));
		when(loans.save(any())).thenThrow(new RuntimeException("Mongo down"));

		assertThatThrownBy(() -> service.borrow("user_1", "inst_1", "item_1"))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode())
						.isEqualTo(ErrorCode.INTERNAL_ERROR));

		verify(copyLease).release("lease_abc");
	}

	// ── duplicate-key race → re-read winner ───────────────────────────

	@Test
	void returnsWinnerLoanOnDuplicateKeyRace() {
		Loan winner = activeLoan("loan_winner", LicenseModel.SUBSCRIPTION);
		when(entitlementQuery.check(any(), eq("item_1"))).thenReturn(subscription());
		when(loans.findByUserIdAndItemIdAndStatus(any(), any(), any()))
				.thenReturn(Optional.empty())       // first check — nothing there
				.thenReturn(Optional.of(winner));    // re-read after race
		when(loans.save(any())).thenThrow(new DuplicateKeyException("E11000"));

		BorrowResult result = service.borrow("user_1", "inst_1", "item_1");

		assertThat(result.created()).isFalse();
		assertThat(result.loan().getLoanId()).isEqualTo("loan_winner");
	}

	// ── helpers ───────────────────────────────────────────────────────

	private Loan activeLoan(String loanId, LicenseModel model) {
		return Loan.builder()
				.loanId(loanId).userId("user_1").itemId("item_1").institutionId("inst_1")
				.licenseModel(model).status(LoanStatus.ACTIVE)
				.canPersist(model != LicenseModel.ELITE)
				.borrowedAt(NOW).build();
	}

	private EntitlementDecision openAccess() {
		return new EntitlementDecision(true, AccessLevel.OPEN_ACCESS, "ent_1", null, 0, null, null);
	}

	private EntitlementDecision subscription() {
		return new EntitlementDecision(true, AccessLevel.ENTITLED_UNLIMITED, "ent_1", null, 14, null, null);
	}

	private EntitlementDecision elite() {
		return new EntitlementDecision(true, AccessLevel.ENTITLED_CONCURRENT, "ent_1", 5, 14, null, null);
	}

	private EntitlementDecision denied() {
		return new EntitlementDecision(false, null, null, null, 0, null, DenyReason.NO_ENTITLEMENT);
	}
}
