package com.tf.reader.catalogue.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.CollectionPublisherIndex;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CollectionPublisherIndexRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.mongo.PublisherMongoClients;

import lombok.RequiredArgsConstructor;

/**
 * {@link CatalogueItemStore}'s counterpart for {@link BookCollection} - same rules, same
 * "deliberately not a second Spring bean" reasoning (see {@link CatalogueItemStore}'s class
 * Javadoc for why), same opt-in-per-publisher routing.
 *
 * <p>Two shapes of query, handled differently:
 * <ul>
 *   <li>{@link #findByPublisherId}, {@link #findByPublisherIdAndCode}, {@link #countByPublisherId}
 *   already know the one publisher to ask (it's a path parameter everywhere they're called), so
 *   they route directly with no index lookup.</li>
 *   <li>{@link #findById}/{@link #findAllById} are keyed by collection ID alone -
 *   {@code CollectionAdminService.setItems} and {@code EntitlementAdminService}'s collection-scope
 *   lookups have no publisher in the request - so they consult
 *   {@link CollectionPublisherIndexRepository} first, the same chicken-and-egg fix
 *   {@code ItemPublisherIndex} is for catalogue items.</li>
 * </ul>
 *
 * <p>{@link #findAll} is the one cross-publisher fan-out here, for
 * {@code CollectionEntitlementAdminService}'s unscoped "every collection" listing: queries the
 * shared repository and every publisher's own database, merges, sorts by name, and paginates over
 * the merged list in memory - the shared database alone was never going to have every collection
 * once collections split by publisher.
 */
@Service
@RequiredArgsConstructor
public class BookCollectionStore {

	private final BookCollectionRepository bookCollectionRepository;
	private final CollectionPublisherIndexRepository collectionPublisherIndexRepository;
	private final PublisherRepository publisherRepository;
	private final PublisherMongoClients publisherMongoClients;
	private final MongoConverter mongoConverter;
	private final Clock clock;

	/**
	 * @throws IllegalArgumentException if {@code collection.getPublisherId()} does not reference an
	 *         existing publisher.
	 */
	public BookCollection save(BookCollection collection) {
		Publisher publisher = publisherRepository.findById(collection.getPublisherId()).orElseThrow(() -> new IllegalArgumentException(
				"BookCollection.publisherId does not reference an existing publisher"));

		if (!hasOwnMongo(publisher)) {
			return bookCollectionRepository.save(collection);
		}

		BookCollection saved = templateFor(publisher).save(collection);
		collectionPublisherIndexRepository
				.save(new CollectionPublisherIndex(saved.getId(), saved.getPublisherId(), clock.instant()));
		return saved;
	}

	public Optional<BookCollection> findById(String collectionId) {
		Optional<Publisher> ownerWithOwnMongo = collectionPublisherIndexRepository.findById(collectionId)
				.flatMap(index -> publisherRepository.findById(index.getPublisherId()))
				.filter(this::hasOwnMongo);

		if (ownerWithOwnMongo.isPresent()) {
			return Optional.ofNullable(templateFor(ownerWithOwnMongo.get()).findById(collectionId, BookCollection.class));
		}
		return bookCollectionRepository.findById(collectionId);
	}

	/** One lookup per id, same reasoning as {@code EntitlementQueryImpl.checkAll}: different ids can belong to different publishers. */
	public List<BookCollection> findAllById(Collection<String> ids) {
		return ids.stream().map(this::findById).flatMap(Optional::stream).toList();
	}

	public boolean existsById(String collectionId) {
		return findById(collectionId).isPresent();
	}

	/** The publisher is already known here (a path parameter everywhere this is called), so no index lookup. */
	public Page<BookCollection> findByPublisherId(String publisherId, Pageable pageable) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher == null || !hasOwnMongo(publisher)) {
			return bookCollectionRepository.findByPublisherId(publisherId, pageable);
		}
		Criteria criteria = Criteria.where("publisherId").is(publisherId);
		MongoTemplate template = templateFor(publisher);
		List<BookCollection> content = template.find(Query.query(criteria).with(pageable), BookCollection.class);
		long total = template.count(Query.query(criteria), BookCollection.class);
		return new PageImpl<>(content, pageable, total);
	}

	public Optional<BookCollection> findByPublisherIdAndCode(String publisherId, String code) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher == null || !hasOwnMongo(publisher)) {
			return bookCollectionRepository.findByPublisherIdAndCode(publisherId, code);
		}
		Query query = Query.query(Criteria.where("publisherId").is(publisherId).and("code").is(code));
		return Optional.ofNullable(templateFor(publisher).findOne(query, BookCollection.class));
	}

	public long countByPublisherId(String publisherId) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher == null || !hasOwnMongo(publisher)) {
			return bookCollectionRepository.countByPublisherId(publisherId);
		}
		return templateFor(publisher).count(Query.query(Criteria.where("publisherId").is(publisherId)),
				BookCollection.class);
	}

	/** Every collection, across the shared database and every publisher's own - see the class Javadoc. */
	public Page<BookCollection> findAll(Pageable pageable) {
		List<BookCollection> all = new ArrayList<>(bookCollectionRepository.findAll());
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			all.addAll(templateFor(publisher).findAll(BookCollection.class));
		}

		Comparator<BookCollection> byName = Comparator.comparing(BookCollection::getName,
				Comparator.nullsLast(Comparator.naturalOrder()));
		List<BookCollection> sorted = all.stream().sorted(byName).toList();

		int from = Math.min((int) pageable.getOffset(), sorted.size());
		int to = Math.min(from + pageable.getPageSize(), sorted.size());
		return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
	}

	private boolean hasOwnMongo(Publisher publisher) {
		return publisher.getMongoUri() != null && !publisher.getMongoUri().isBlank();
	}

	private MongoTemplate templateFor(Publisher publisher) {
		return new MongoTemplate(publisherMongoClients.factoryFor(publisher.getId(), publisher.getMongoUri()),
				mongoConverter);
	}

}
