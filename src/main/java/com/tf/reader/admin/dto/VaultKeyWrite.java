package com.tf.reader.admin.dto;

/**
 * A publisher's own AES-256 vault key, base64-encoded, or {@code null} to revert to T&F's shared
 * master key. Write-only: never echoed back by any endpoint, same discipline as
 * {@code AdminUserUpdate}'s password field.
 */
public record VaultKeyWrite(String keyBase64) {
}
