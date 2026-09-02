package com.tf.reader.ingest.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backblaze B2 connection details, bound from {@code tf.storage.*}. Path-style addressing is the
 * safer default for a non-AWS S3-compatible endpoint (Backblaze supports both), so it defaults to
 * on and only needs setting explicitly to turn off for a provider that requires virtual-hosted
 * style instead.
 */
@ConfigurationProperties(prefix = "tf.storage")
public record BookStorageProperties(String endpoint, String region, String bucket, String accessKeyId,
		String secretAccessKey, Boolean pathStyle) {

	public BookStorageProperties {
		pathStyle = pathStyle == null ? Boolean.TRUE : pathStyle;
	}

	/** Redacts the credentials, same reasoning as {@code CryptoProperties} redacting the master key. */
	@Override
	public String toString() {
		return "BookStorageProperties[endpoint=" + endpoint + ", region=" + region + ", bucket=" + bucket
				+ ", accessKeyId=<redacted>, secretAccessKey=<redacted>, pathStyle=" + pathStyle + "]";
	}
}
