package com.tf.reader.catalogue.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;

/**
 * {@link ItemPublisherIndex}'s counterpart for {@link BookCollection}: a standing, always-shared
 * answer to "which publisher owns this collection ID", consulted before knowing which database to
 * ask. Needed for the same reason - {@code CollectionAdminService.setItems} and
 * {@code EntitlementAdminService}'s collection-scope lookups are keyed by collection ID alone, with
 * no publisher in the request to route by directly.
 */
@Document(collection = "collectionPublisherIndex")
@Getter
public class CollectionPublisherIndex {

	@Id
	private final String collectionId;

	private final String publisherId;
	private final Instant updatedAt;

	public CollectionPublisherIndex(String collectionId, String publisherId, Instant updatedAt) {
		this.collectionId = collectionId;
		this.publisherId = publisherId;
		this.updatedAt = updatedAt;
	}

}
