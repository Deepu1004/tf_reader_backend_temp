package com.tf.reader.crypto.service;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.tf.reader.crypto.api.VaultKeyAdmin;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class VaultKeyAdminImpl implements VaultKeyAdmin {

	private final PublisherVaultKeyStore vaultKeyStore;

	@Override
	public void setKey(String publisherId, byte[] rawAesKey) {
		vaultKeyStore.save(publisherId, new SecretKeySpec(rawAesKey, "AES"));
	}

	@Override
	public void clearKey(String publisherId) {
		vaultKeyStore.clear(publisherId);
	}

}
