package com.tf.reader.catalogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;

/**
 * A standing, always-shared answer to "which publisher owns this item ID", kept in sync with
 * {@link CatalogueItem} but never itself split by publisher.
 *
 * <p>Once catalogue data moves into a database per publisher, nothing can pick which database to
 * ask until it already knows the answer that database was supposed to give it. This collection
 * exists to be consulted first, before that split happens to the real record.
 *
 * <p>Kept up to date by {@link ItemPublisherIndexSync} on every {@code CatalogueItem} save through
 * the shared repository, and directly by {@code CatalogueItemStore} for a save that routes to a
 * publisher's own database instead. Read by {@code CatalogueItemStore.findById} to resolve which
 * database to ask before the real record is available to say so itself.
 */
@Document(collection = "itemPublisherIndex")
@Getter
public class ItemPublisherIndex {

	@Id
	private final String itemId;

	private final String publisherId;
	private final Instant updatedAt;

	public ItemPublisherIndex(String itemId, String publisherId, Instant updatedAt) {
		this.itemId = itemId;
		this.publisherId = publisherId;
		this.updatedAt = updatedAt;
	}

}
