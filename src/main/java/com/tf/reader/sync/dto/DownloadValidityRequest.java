package com.tf.reader.sync.dto;

import jakarta.validation.constraints.NotNull;

public record DownloadValidityRequest(
        @NotNull(message = "isValid is required")
        Boolean isValid) {
}
