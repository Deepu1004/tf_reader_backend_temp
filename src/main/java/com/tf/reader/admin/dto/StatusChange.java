package com.tf.reader.admin.dto;

import com.tf.reader.common.model.RecordStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The StatusChange schema: the body sent to {@code PATCH
 * /publishers/{id}/status}.
 */
public record StatusChange(@NotNull RecordStatus status, @Size(max = 500) String reason) {
}
