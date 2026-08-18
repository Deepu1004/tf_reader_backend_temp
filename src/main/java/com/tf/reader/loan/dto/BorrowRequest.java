package com.tf.reader.loan.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/loans}.
 * The client sends only the item it wants — license model and copy limits are
 * decided by the entitlement check, never by the client (D-009).
 */
public record BorrowRequest(@NotBlank String itemId) {
}
