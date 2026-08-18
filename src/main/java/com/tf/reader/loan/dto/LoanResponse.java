package com.tf.reader.loan.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tf.reader.loan.entity.LicenseModel;
import com.tf.reader.loan.entity.Loan;
import com.tf.reader.loan.entity.LoanStatus;

/**
 * Wire shape for a loan — matches the frozen flambeau {@code Loan} schema plus
 * {@code serverTime} on every response (invariant #4).
 *
 * <p>Nullable fields are omitted rather than sent as null (contract convention).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoanResponse(
		String loanId,
		String itemId,
		String userId,
		String institutionId,
		LicenseModel licenseModel,
		LoanStatus status,
		boolean canPersist,
		Instant borrowedAt,
		Instant dueAt,
		Instant returnedAt,
		Instant expiredAt,
		Instant serverTime) {

	public static LoanResponse from(Loan loan, Instant serverTime) {
		return new LoanResponse(
				loan.getLoanId(),
				loan.getItemId(),
				loan.getUserId(),
				loan.getInstitutionId(),
				loan.getLicenseModel(),
				loan.getStatus(),
				loan.isCanPersist(),
				loan.getBorrowedAt(),
				loan.getDueAt(),
				loan.getReturnedAt(),
				loan.getExpiredAt(),
				serverTime);
	}
}
