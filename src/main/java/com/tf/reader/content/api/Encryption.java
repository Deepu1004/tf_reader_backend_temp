package com.tf.reader.content.api;

public record Encryption(
        String algorithm,
        String layout,
        String wrappedBek,
        String wrapAlgorithm,
        String keyId,
        String keyFingerprint
) {
}
