package com.tf.reader.admin.dto;

import com.tf.reader.catalogue.entity.EntitlementStatus;

import jakarta.validation.constraints.NotNull;

public record EntitlementStatusChange(@NotNull EntitlementStatus status, String reason) {
}
