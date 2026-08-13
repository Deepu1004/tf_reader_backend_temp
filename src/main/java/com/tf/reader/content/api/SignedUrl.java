package com.tf.reader.content.api;

import java.time.Instant;

public record SignedUrl(
        String url,
        Instant expiresAt,
        Long cipherLength,
        Long originalLength,
        String mimeType
) {
}
