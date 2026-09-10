package com.tf.reader.library.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A loan as the library screen needs it.
 *
 * <p>The API Reference's {@code Loan} minus {@code userId}, {@code institutionId} and its own
 * {@code serverTime}: the reader is the caller, and one {@code serverTime} for the whole response is
 * what every countdown on the screen is measured against.
 *
 * <p><b>{@code licenceModel} and {@code status} are strings here on purpose.</b> The loan module has
 * real enums, but importing another lane's {@code entity} package is the coupling an {@code api}
 * package exists to prevent. The sanctioned seam type {@code loan.api.ActiveLoanView} already
 * publishes both as strings — it carries {@code status} and {@code borrowedAt} since D-026 — so the
 * assembler forwards them straight through without ever touching loan's enums.
 *
 * @param dueAt      absent for open access, which never expires
 * @param canPersist what the download button reads. Never the tier: {@code ELITE} is false, and the
 *                   server refuses a DOWNLOAD intent whatever the UI offered
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LibraryLoan(
		String loanId,
		String itemId,
		String licenceModel,
		String status,
		Instant borrowedAt,
		Instant dueAt,
		boolean canPersist) {
}
