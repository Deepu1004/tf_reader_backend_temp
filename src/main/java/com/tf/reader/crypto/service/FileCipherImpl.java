package com.tf.reader.crypto.service;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Service;

import com.tf.reader.crypto.api.FileCipher;

/**
 * See {@link FileCipher} for the contract and the {@code nonce(12) || ciphertext || tag(16)}
 * layout — {@code src/features/encryption/cipherLayout.ts} on the app side must keep matching it.
 */
@Service
class FileCipherImpl implements FileCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;

	@Override
	public byte[] encrypt(SecretKey bek, byte[] plaintext) {
		byte[] nonce = new byte[NONCE_BYTES];
		strongRandom().nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, bek, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(plaintext);

			byte[] result = new byte[NONCE_BYTES + ciphertext.length];
			System.arraycopy(nonce, 0, result, 0, NONCE_BYTES);
			System.arraycopy(ciphertext, 0, result, NONCE_BYTES, ciphertext.length);
			return result;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to encrypt content under the book key.", e);
		}
	}

	@Override
	public byte[] decrypt(SecretKey bek, byte[] ciphertext) {
		if (ciphertext.length < NONCE_BYTES) {
			throw new IllegalStateException("Ciphertext is shorter than the nonce it must carry.");
		}
		byte[] nonce = new byte[NONCE_BYTES];
		System.arraycopy(ciphertext, 0, nonce, 0, NONCE_BYTES);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, bek, new GCMParameterSpec(TAG_BITS, nonce));
			return cipher.doFinal(ciphertext, NONCE_BYTES, ciphertext.length - NONCE_BYTES);
		} catch (AEADBadTagException e) {
			throw new IllegalStateException(
					"Ciphertext failed authentication: truncated, corrupted, or the wrong key.", e);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Failed to decrypt content under the book key.", e);
		}
	}

	private static SecureRandom strongRandom() {
		try {
			return SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("No strong SecureRandom algorithm is available.", e);
		}
	}

}
