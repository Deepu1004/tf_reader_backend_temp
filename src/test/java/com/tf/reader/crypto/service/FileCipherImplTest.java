package com.tf.reader.crypto.service;

import java.nio.charset.StandardCharsets;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileCipherImplTest {

	private static final int CIPHER_OVERHEAD_BYTES = 28;

	private final FileCipherImpl cipher = new FileCipherImpl();
	private final SecretKey bek = aesKey();

	private static SecretKey aesKey() {
		try {
			KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
			keyGenerator.init(256);
			return keyGenerator.generateKey();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void roundTripsAFileWithTheExpectedTwentyEightByteOverhead() {
		byte[] plaintext = "the quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);

		byte[] encrypted = cipher.encrypt(bek, plaintext);
		byte[] decrypted = cipher.decrypt(bek, encrypted);

		assertThat(encrypted).hasSize(plaintext.length + CIPHER_OVERHEAD_BYTES);
		assertThat(decrypted).isEqualTo(plaintext);
	}

	@Test
	void usesAFreshNonceEveryTime() {
		byte[] plaintext = "same plaintext, twice".getBytes(StandardCharsets.UTF_8);

		byte[] first = cipher.encrypt(bek, plaintext);
		byte[] second = cipher.encrypt(bek, plaintext);

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void rejectsCiphertextThatFailsAuthentication() {
		byte[] tampered = cipher.encrypt(bek, "tamper with me".getBytes(StandardCharsets.UTF_8));
		tampered[tampered.length - 1] ^= 1;

		assertThatThrownBy(() -> cipher.decrypt(bek, tampered)).isInstanceOf(IllegalStateException.class);
	}

}
