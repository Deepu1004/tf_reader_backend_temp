package com.tf.reader.admin.dto;

import com.tf.reader.common.model.RecordStatus;

/** A new status to set on a record, with an optional note explaining why. */
public record StatusChange(RecordStatus status, String reason) {}
