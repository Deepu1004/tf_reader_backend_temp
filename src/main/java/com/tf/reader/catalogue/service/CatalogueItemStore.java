package com.tf.reader.catalogue.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ItemPublisherIndex;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.WorkType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.ItemPublisherIndexRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.mongo.PublisherMongoClients;

import lombok.RequiredArgsConstructor;

/**
 * Routes a single catalogue item's save/read to a publisher's own MongoDB if they've configured
 * one, or to the shared database otherwise - opt-in, per publisher, same rule as
 * {@code PublisherVaultKeyStore}'s crypto side of this plan.
 *
 * <p><b>Deliberately not a second {@code MongoTemplate}/{@code MongoDatabaseFactory} Spring
 * bean.</b> An earlier attempt at this exact routing tried exactly that, and it disabled Spring
 * Boot's entire Mongo auto-configuration chain - {@code MongoAutoConfiguration} itself is
 * {@code @ConditionalOnMissingBean(MongoDatabaseFactory.class)} at the class level in this Spring
 * Boot version, so defining a second bean of that type takes down the {@code MongoClient} bean
 * with it, not just the repository layer. This class instead builds a plain, unregistered
 * {@link MongoTemplate} per publisher on demand (via {@link PublisherMongoClients}, which already
 * caches the underlying connection) and calls it directly - nothing here is ever visible to
 * Spring's bean graph, so none of that auto-configuration is touched.
 *
 * <p>One consequence of that: {@code CatalogueItemPersistenceGuard} (the
 * {@code BeforeConvertCallback} that rejects a missing/unknown {@code publisherId}) only runs for
 * saves through the shared, Spring-managed {@code MongoTemplate} - a manually-built one never
 * receives Spring's entity callbacks. {@link #save} re-checks the same invariant by hand before
 * routing, so the guard's effect is preserved either way.
 *
 * <p><b>What this is wired into, and what it is not (yet)</b>: {@code CatalogueItemAdminService}
 * (create/update/get), {@code ContentAccessGrantImpl}, {@code EntitlementQueryImpl},
 * {@code IngestProcessor} (including its two scan queries, {@link #findByContentState} and
 * {@link #findByContentStateInAndUpdatedAtBefore} - see their Javadoc for what "scan" means once
 * more than one database exists), and {@code CoverImageService}. Admin entitlements and the OPDS
 * feeds still read/write {@link CatalogueItemRepository} directly - an item routed to a
 * publisher's own database will not be found by those paths yet. That is a deliberate, narrower
 * scope for this pass, not an oversight: migrating every one of those is a larger, separate piece
 * of work.
 */
@Service
@RequiredArgsConstructor
public class CatalogueItemStore {

	private final CatalogueItemRepository catalogueItemRepository;
	private final ItemPublisherIndexRepository itemPublisherIndexRepository;
	private final PublisherRepository publisherRepository;
	private final PublisherMongoClients publisherMongoClients;
	private final MongoConverter mongoConverter;
	private final Clock clock;

	/**
	 * @throws IllegalArgumentException if {@code item.getPublisherId()} does not reference an
	 *         existing publisher - the same check {@code CatalogueItemPersistenceGuard} makes for
	 *         a shared-database save, re-implemented here since a publisher's own database never
	 *         sees that guard.
	 */
	public CatalogueItem save(CatalogueItem item) {
		Publisher publisher = publisherRepository.findById(item.getPublisherId()).orElseThrow(
				() -> new IllegalArgumentException("CatalogueItem.publisherId does not reference an existing publisher"));

		if (!hasOwnMongo(publisher)) {
			// Goes through the shared, Spring-managed MongoTemplate, so both
			// CatalogueItemPersistenceGuard and ItemPublisherIndexSync already run as normal.
			return catalogueItemRepository.save(item);
		}

		CatalogueItem saved = templateFor(publisher).save(item);
		// ItemPublisherIndexSync only listens for saves through the shared repository, so a
		// publisher's own database has to update the index itself.
		itemPublisherIndexRepository.save(new ItemPublisherIndex(saved.getId(), saved.getPublisherId(), clock.instant()));
		return saved;
	}

	/**
	 * Consults {@link ItemPublisherIndexRepository} first to learn which publisher owns this item,
	 * then asks that publisher's own database if they have one - the chicken-and-egg fix the
	 * plan's shared index exists for. An item with no index entry (never saved through this store)
	 * falls back to the shared repository directly.
	 */
	public Optional<CatalogueItem> findById(String itemId) {
		Optional<Publisher> ownerWithOwnMongo = itemPublisherIndexRepository.findById(itemId)
				.flatMap(index -> publisherRepository.findById(index.getPublisherId()))
				.filter(this::hasOwnMongo);

		if (ownerWithOwnMongo.isPresent()) {
			return Optional.ofNullable(templateFor(ownerWithOwnMongo.get()).findById(itemId, CatalogueItem.class));
		}
		return catalogueItemRepository.findById(itemId);
	}

	public boolean existsById(String itemId) {
		return findById(itemId).isPresent();
	}

	/** One lookup per id, same reasoning as {@code EntitlementQueryImpl.checkAll}: different ids can belong to different publishers. */
	public List<CatalogueItem> findAllById(Collection<String> ids) {
		return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
	}

	/** The publisher is already known here (a path parameter/entitlement scope), so no index lookup. */
	public long countByPublisherId(String publisherId) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher == null || !hasOwnMongo(publisher)) {
			return catalogueItemRepository.countByPublisherId(publisherId);
		}
		return templateFor(publisher).count(Query.query(Criteria.where("publisherId").is(publisherId)),
				CatalogueItem.class);
	}

	/**
	 * A collection ID alone doesn't say which publisher's database its items live in, so this fans
	 * out like {@link #findByContentState} rather than resolving a single publisher first - a
	 * collection and its items are expected to co-locate, but summing every database's count is
	 * correct even if that ever weren't true, since every non-matching database just contributes 0.
	 */
	public long countByCollectionIds(String collectionId) {
		long total = catalogueItemRepository.countByCollectionIds(collectionId);
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			total += templateFor(publisher).count(Query.query(Criteria.where("collectionIds").is(collectionId)),
					CatalogueItem.class);
		}
		return total;
	}

	/** Same fan-out as {@link #findByContentState}, for {@code CollectionAdminService.setItems}'s current-members lookup. */
	public List<CatalogueItem> findByCollectionIds(String collectionId) {
		List<CatalogueItem> items = new ArrayList<>(catalogueItemRepository.findByCollectionIds(collectionId));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher).find(Query.query(Criteria.where("collectionIds").is(collectionId)),
					CatalogueItem.class));
		}
		return items;
	}

	/**
	 * An ISBN alone doesn't say which publisher's database its book lives in, so this fans out like
	 * {@link #findByContentState} - needed by {@code CatalogueItemAdminService.requireIsbnFree} so a
	 * duplicate isn't missed just because the existing book is in a publisher's own database.
	 */
	public Optional<CatalogueItem> findByIsbn(String isbn) {
		Optional<CatalogueItem> shared = catalogueItemRepository.findByIsbn(isbn);
		if (shared.isPresent()) {
			return shared;
		}
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			CatalogueItem found = templateFor(publisher)
					.findOne(Query.query(Criteria.where("isbn").is(isbn)), CatalogueItem.class);
			if (found != null) {
				return Optional.of(found);
			}
		}
		return Optional.empty();
	}

	/** The publisher is already known here (an entitlement's scopeId), same reasoning as {@link #countByPublisherId}. */
	public List<CatalogueItem> findByPublisherIdAndStatus(String publisherId, ItemStatus status) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher == null || !hasOwnMongo(publisher)) {
			return catalogueItemRepository.findByPublisherIdAndStatus(publisherId, status);
		}
		return templateFor(publisher).find(
				Query.query(Criteria.where("publisherId").is(publisherId).and("status").is(status)),
				CatalogueItem.class);
	}

	/** Same fan-out as {@link #findByCollectionIds}, for an entitlement scoped to one collection. */
	public List<CatalogueItem> findByCollectionIdsAndStatusAndContentState(String collectionId, ItemStatus status,
			ContentState contentState) {
		List<CatalogueItem> items = new ArrayList<>(
				catalogueItemRepository.findByCollectionIdsAndStatusAndContentState(collectionId, status, contentState));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher)
					.find(Query.query(Criteria.where("collectionIds").is(collectionId).and("status").is(status)
							.and("contentState").is(contentState)), CatalogueItem.class));
		}
		return items;
	}

	/** Same fan-out, for the open-access count every institution can reach with no entitlement at all. */
	public List<CatalogueItem> findByAccessTierAndStatus(AccessTier accessTier, ItemStatus status) {
		List<CatalogueItem> items = new ArrayList<>(catalogueItemRepository.findByAccessTierAndStatus(accessTier, status));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher).find(
					Query.query(Criteria.where("accessTier").is(accessTier).and("status").is(status)),
					CatalogueItem.class));
		}
		return items;
	}

	/** Same fan-out, for the OPDS root feed's top-level Journal signposts. */
	public List<CatalogueItem> findByWorkTypeAndStatus(WorkType workType, ItemStatus status) {
		List<CatalogueItem> items = new ArrayList<>(catalogueItemRepository.findByWorkTypeAndStatus(workType, status));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher).find(
					Query.query(Criteria.where("workType").is(workType).and("status").is(status)), CatalogueItem.class));
		}
		return items;
	}

	/**
	 * Same fan-out, for the OPDS "all" group (the whole entitled catalogue). {@code sort} is
	 * applied in memory after merging, since a per-database sort says nothing about the combined
	 * order - see {@link #sortInMemory} for the two fields OPDS ever asks for.
	 */
	public List<CatalogueItem> findByStatusAndContentState(ItemStatus status, ContentState contentState, Sort sort) {
		List<CatalogueItem> items = new ArrayList<>(
				catalogueItemRepository.findByStatusAndContentState(status, contentState, Sort.unsorted()));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher).find(
					Query.query(Criteria.where("status").is(status).and("contentState").is(contentState)),
					CatalogueItem.class));
		}
		return sortInMemory(items, sort);
	}

	/** Same fan-out, for the anonymous public catalogue (open access only). */
	public List<CatalogueItem> findByAccessTierAndStatusAndContentState(AccessTier accessTier, ItemStatus status,
			ContentState contentState, Sort sort) {
		List<CatalogueItem> items = new ArrayList<>(catalogueItemRepository
				.findByAccessTierAndStatusAndContentState(accessTier, status, contentState, Sort.unsorted()));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher)
					.find(Query.query(Criteria.where("accessTier").is(accessTier).and("status").is(status)
							.and("contentState").is(contentState)), CatalogueItem.class));
		}
		return sortInMemory(items, sort);
	}

	/**
	 * Same fan-out, for a container's children (a Journal's Volumes, a Volume's Issues, an Issue's
	 * Articles). Callers already re-sort by sequence afterwards - Mongo makes no cross-shard
	 * ordering guarantee here even before this fans out across databases.
	 */
	public List<CatalogueItem> findByParentId(String parentId) {
		List<CatalogueItem> items = new ArrayList<>(catalogueItemRepository.findByParentId(parentId));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher).find(Query.query(Criteria.where("parentId").is(parentId)),
					CatalogueItem.class));
		}
		return items;
	}

	/**
	 * {@code title} and {@code publishedAt} are the only two fields anything in this codebase ever
	 * sorts a {@code CatalogueItem} list by ({@code OpdsCatalogueQuery.resolveSort}) - a per-database
	 * Mongo sort says nothing about the merged order, so this re-sorts the merged list in memory
	 * instead. {@code Sort.unsorted()} is a no-op, same as leaving the merged order as-is.
	 */
	private static List<CatalogueItem> sortInMemory(List<CatalogueItem> items, Sort sort) {
		if (sort == null || sort.isUnsorted()) {
			return items;
		}
		Comparator<CatalogueItem> comparator = null;
		for (Sort.Order order : sort) {
			Comparator<CatalogueItem> fieldComparator = switch (order.getProperty()) {
				case "title" -> Comparator.comparing(CatalogueItem::getTitle,
						Comparator.nullsLast(Comparator.naturalOrder()));
				case "publishedAt" -> Comparator.comparing(CatalogueItem::getPublishedAt,
						Comparator.nullsLast(Comparator.naturalOrder()));
				default -> throw new IllegalArgumentException(
						"No cross-publisher sort support for property: " + order.getProperty());
			};
			if (order.isDescending()) {
				fieldComparator = fieldComparator.reversed();
			}
			comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
		}
		return items.stream().sorted(comparator).toList();
	}

	/**
	 * Every item in {@code state}, in the shared database and in every publisher's own database
	 * that has one configured. {@code IngestProcessor}'s poll loop needs this because a book is
	 * queued wherever its publisher's catalogue lives, not only in the shared database - unlike
	 * {@link #findById}, there is no index to consult first, since the whole point is finding items
	 * whose ID isn't known yet. A narrow, purpose-built fan-out for this one query shape, not the
	 * general cross-publisher search/pagination problem ({@code CatalogueItemSearchRepository} and
	 * the public catalogue) - those still read the shared database only.
	 */
	public List<CatalogueItem> findByContentState(ContentState state) {
		List<CatalogueItem> items = new ArrayList<>(catalogueItemRepository.findByContentState(state));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			items.addAll(templateFor(publisher).find(Query.query(Criteria.where("contentState").is(state)),
					CatalogueItem.class));
		}
		return items;
	}

	/** Same fan-out as {@link #findByContentState}, for the watchdog's stuck-item query. */
	public List<CatalogueItem> findByContentStateInAndUpdatedAtBefore(List<ContentState> states, Instant updatedAt) {
		List<CatalogueItem> items = new ArrayList<>(
				catalogueItemRepository.findByContentStateInAndUpdatedAtBefore(states, updatedAt));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			Query query = Query.query(Criteria.where("contentState").in(states).and("updatedAt").lt(updatedAt));
			items.addAll(templateFor(publisher).find(query, CatalogueItem.class));
		}
		return items;
	}

	private boolean hasOwnMongo(Publisher publisher) {
		return publisher.getMongoUri() != null && !publisher.getMongoUri().isBlank();
	}

	private MongoTemplate templateFor(Publisher publisher) {
		return new MongoTemplate(publisherMongoClients.factoryFor(publisher.getId(), publisher.getMongoUri()),
				mongoConverter);
	}

}
