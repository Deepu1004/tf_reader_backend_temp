package com.tf.reader.hold.dto;

import java.time.Instant;

// Response body for POST /api/v1/holds/{holdId}/accept — "the loan, same
// shape as borrowing" per the contract. Mirrors loan.dto.BorrowResponse's
// actual fields exactly rather than the Loan schema's, since hold may not
// import loan's dto/ package, and this is what borrowing actually returns
// today. Note: the contract's Loan schema also requires userId, which
// BorrowResponse itself omits — a pre-existing mismatch in loan's own
// module, not introduced here.
public record AcceptedLoanResponse(
        String loanId, String itemId, String licenceModel, String status,
        boolean canPersist, Instant borrowedAt, Instant dueAt, Instant serverTime) {
}
