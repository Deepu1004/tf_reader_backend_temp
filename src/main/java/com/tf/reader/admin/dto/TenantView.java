package com.tf.reader.admin.dto;

import com.tf.reader.catalogue.entity.VaultConnectionHealth;

/**
 * Not "what books does this publisher have" ({@link PublisherView} already answers that) but
 * "which database and vault does this publisher use, and is it healthy" - the platform's own
 * infrastructure bookkeeping about a publisher, kept separate rather than bolted onto the
 * publisher's business-data endpoints.
 */
public record TenantView(String id, String code, String name, String vaultRef,
		VaultConnectionHealth connectionHealth) {
}
