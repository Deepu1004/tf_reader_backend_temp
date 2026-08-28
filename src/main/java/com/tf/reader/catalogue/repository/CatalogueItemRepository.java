package com.tf.reader.catalogue.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ItemStatus;

public interface CatalogueItemRepository extends MongoRepository<CatalogueItem, String> {

	List<CatalogueItem> findByPublisherIdAndStatus(String publisherId, ItemStatus status);

	List<CatalogueItem> findByCollectionIdsAndStatusAndContentState(String collectionId, ItemStatus status,
			ContentState contentState);

	// Backs the OPDS "all" group: the whole entitled catalogue, sorted, entitlement
	// filtering applied afterwards in Java since EntitlementQuery is single-item only.
	List<CatalogueItem> findByStatusAndContentState(ItemStatus status, ContentState contentState, Sort sort);

	List<CatalogueItem> findByCollectionIds(String collectionId);

	List<CatalogueItem> findByAccessTierAndStatus(AccessTier accessTier, ItemStatus status);

	// Backs the anonymous public catalogue: open access only, no institution to scope by.
	List<CatalogueItem> findByAccessTierAndStatusAndContentState(AccessTier accessTier, ItemStatus status,
			ContentState contentState, Sort sort);

	Optional<CatalogueItem> findByIsbn(String isbn);

	List<CatalogueItem> findAllBy(TextCriteria criteria);

	long countByPublisherId(String publisherId);

	long countByCollectionIds(String collectionId);

	// Backs IngestProcessor's queue drain: every item waiting for background processing.
	List<CatalogueItem> findByContentState(ContentState contentState);

	// Backs the ingest watchdog: anything left in one of these states past the timeout.
	List<CatalogueItem> findByContentStateInAndUpdatedAtBefore(List<ContentState> contentStates, Instant updatedAt);

}
