package com.tf.reader.catalogue.entity;

/**
 * Whether {@link Publisher#getVaultRef()} points at a key vault T&F can currently reach.
 *
 * <p>Nothing computes {@code HEALTHY} or {@code UNREACHABLE} yet: there is no KMS integration to
 * check against. Every publisher reads as {@code NOT_CONFIGURED} until that lands.
 */
public enum VaultConnectionHealth {
	NOT_CONFIGURED,
	HEALTHY,
	UNREACHABLE
}
