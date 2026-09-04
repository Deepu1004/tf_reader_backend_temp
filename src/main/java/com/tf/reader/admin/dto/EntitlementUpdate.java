package com.tf.reader.admin.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Below is the EntitlementUpdate schema. It includes fields for copies, loan period days, valid from and valid to dates, and a version number.
//
// copies and validTo stay nullable on purpose: null is a real value here (UNLIMITED copies, an
// open-ended validTo), not an omission. loanPeriodDays and validFrom have no such meaning for
// null, so they are required - this is a full-replace PUT, and without @NotNull here a caller
// that leaves either field out silently nulls it on the saved entitlement instead of getting a
// 400.
public record EntitlementUpdate(
		@Min(1) Integer copies,
		@NotNull @Min(1) Integer loanPeriodDays,
		@NotNull LocalDate validFrom,
		LocalDate validTo,
		@NotNull Long version) {
}
