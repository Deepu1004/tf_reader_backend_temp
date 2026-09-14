package com.tf.reader.admin.dto;

/**
 * A publisher's own MongoDB connection string, or {@code null} to revert to T&F's shared database.
 * No {@code @NotNull}: unlike most writes in this package, {@code null} is a meaningful, valid
 * value here, not a missing field.
 */
public record DatabaseWrite(String mongoUri) {
}
