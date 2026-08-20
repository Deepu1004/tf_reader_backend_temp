package com.tf.reader.admin.dto;

import com.tf.reader.common.model.RecordStatus;

import jakarta.validation.constraints.NotNull;

/** A new status to set on a record, with an optional note explaining why. */
public record StatusChange(@NotNull RecordStatus status, String reason) {}
