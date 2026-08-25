package com.tf.reader.crypto;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the master key once at startup. {@link #masterKey(CryptoProperties)} fails fast: a
 * generated fallback here would silently encrypt book keys under a key only this one process
 * instance ever knew, which is worse than not starting at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

	/** AES-256 requires exactly 32 key bytes. */
	private static final int MASTER_KEY_BYTES = 32;

	@Bean
	SecretKey masterKey(CryptoProperties properties) {
		String base64 = properties.masterKey();
		if (base64 == null || base64.isBlank()) {
			throw new IllegalStateException(
					"tf.crypto.master-key is not configured. Set the TF_MASTER_KEY environment "
							+ "variable to a base64-encoded 32-byte AES key (openssl rand -base64 32).");
		}
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64);
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("tf.crypto.master-key is not valid base64.", e);
		}
		if (raw.length != MASTER_KEY_BYTES) {
			throw new IllegalStateException("tf.crypto.master-key must decode to " + MASTER_KEY_BYTES
					+ " bytes but was " + raw.length + " bytes.");
		}
		return new SecretKeySpec(raw, "AES");
	}

}
