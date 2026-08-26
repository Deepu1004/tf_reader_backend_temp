package com.tf.reader.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Master-key and device-key policy configuration, bound from {@code tf.crypto.*}.
 *
 * @param masterKey        base64 of the 32-byte AES master key; must be set via the
 *                         TF_MASTER_KEY environment variable, no default
 * @param masterKeyId      identifies which master key a {@code masterWrappedBek} was wrapped
 *                         under, so a future key rotation can tell old and new apart
 * @param deviceKeyMinBits the smallest RSA modulus, in bits, this server will wrap a BEK to
 */
@ConfigurationProperties(prefix = "tf.crypto")
public record CryptoProperties(String masterKey, String masterKeyId, Integer deviceKeyMinBits) {

	private static final String DEFAULT_MASTER_KEY_ID = "master-v1";
	private static final int DEFAULT_DEVICE_KEY_MIN_BITS = 2048;

	public CryptoProperties {
		masterKeyId = (masterKeyId == null || masterKeyId.isBlank()) ? DEFAULT_MASTER_KEY_ID : masterKeyId;
		deviceKeyMinBits = deviceKeyMinBits == null ? DEFAULT_DEVICE_KEY_MIN_BITS : deviceKeyMinBits;
	}

	/**
	 * Redacts the master key, for the same reason {@code common.security.JwtProperties} would
	 * redact its signing secret: anything that can read this value can decrypt every book on the
	 * platform.
	 */
	@Override
	public String toString() {
		return "CryptoProperties[masterKey=<redacted>, masterKeyId=" + masterKeyId
				+ ", deviceKeyMinBits=" + deviceKeyMinBits + "]";
	}
}
