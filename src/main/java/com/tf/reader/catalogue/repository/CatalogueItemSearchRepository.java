package com.tf.reader.catalogue.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.common.mongo.PublisherMongoClients;

/**
 * The admin's search, scoped to one publisher when the caller names one, or every publisher when
 * they don't (an unscoped SUPER_ADMIN search).
 *
 * <p>Two different shapes of query as a result:
 * <ul>
 *   <li>{@code publisherId} given: no fan-out at all - resolved to that one publisher's own Mongo
 *   if they have one, the shared database otherwise, same as {@code CatalogueItemStore}'s other
 *   single-publisher methods.</li>
 *   <li>{@code publisherId} null: fans out across the shared database and every publisher's own
 *   Mongo, merges, re-sorts by title in memory (the only sort this search ever applies), and
 *   paginates over the merged list - the shared database alone stopped being "everyone's books"
 *   the moment any publisher opted into their own.</li>
 * </ul>
 */
@Repository
public class CatalogueItemSearchRepository {

	private static final Pattern ISBN_SHAPED = Pattern.compile("^(97[89])?[0-9]{9}[0-9X]$");

	private final MongoTemplate mongo;
	private final PublisherRepository publisherRepository;
	private final PublisherMongoClients publisherMongoClients;
	private final MongoConverter mongoConverter;

	public CatalogueItemSearchRepository(MongoTemplate mongo, PublisherRepository publisherRepository,
			PublisherMongoClients publisherMongoClients, MongoConverter mongoConverter) {
		this.mongo = mongo;
		this.publisherRepository = publisherRepository;
		this.publisherMongoClients = publisherMongoClients;
		this.mongoConverter = mongoConverter;
	}

	public record Results(List<CatalogueItem> items, long total) {
	}

	public Results search(String publisherId, String collectionId, ContentType contentType, AccessTier accessTier,
			String q, int page, int size) {
		List<Criteria> parts = new ArrayList<>();
		if (collectionId != null) {
			parts.add(Criteria.where("collectionIds").is(collectionId));
		}
		if (contentType != null) {
			parts.add(Criteria.where("contentType").is(contentType));
		}
		if (accessTier != null) {
			parts.add(Criteria.where("accessTier").is(accessTier));
		}
		if (q != null && !q.isBlank()) {
			parts.add(qCriteria(q.trim()));
		}

		return publisherId != null ? searchOnePublisher(publisherId, parts, page, size)
				: searchEveryPublisher(parts, page, size);
	}

	private Results searchOnePublisher(String publisherId, List<Criteria> parts, int page, int size) {
		List<Criteria> scoped = new ArrayList<>(parts);
		scoped.add(Criteria.where("publisherId").is(publisherId));

		MongoTemplate template = publisherRepository.findById(publisherId).filter(this::hasOwnMongo)
				.map(this::templateFor).orElse(mongo);

		long total = template.count(buildQuery(scoped), CatalogueItem.class);
		Query page1 = buildQuery(scoped).with(Sort.by(Sort.Direction.ASC, "title"));
		page1.skip((long) page * size).limit(size);
		return new Results(template.find(page1, CatalogueItem.class), total);
	}

	private Results searchEveryPublisher(List<Criteria> parts, int page, int size) {
		List<CatalogueItem> all = new ArrayList<>(mongo.find(buildQuery(parts), CatalogueItem.class));
		for (Publisher publisher : publisherRepository.findByMongoUriIsNotNull()) {
			all.addAll(templateFor(publisher).find(buildQuery(parts), CatalogueItem.class));
		}
		List<CatalogueItem> sorted = all.stream()
				.sorted(Comparator.comparing(CatalogueItem::getTitle, Comparator.nullsLast(Comparator.naturalOrder())))
				.toList();

		int from = Math.min(page * size, sorted.size());
		int to = Math.min(from + size, sorted.size());
		return new Results(sorted.subList(from, to), sorted.size());
	}

	private static Query buildQuery(List<Criteria> parts) {
		return parts.isEmpty() ? new Query() : new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
	}

	private Criteria qCriteria(String q) {
		String normalisedIsbn = q.replaceAll("[\\s-]", "").toUpperCase();
		if (ISBN_SHAPED.matcher(normalisedIsbn).matches()) {
			return Criteria.where("isbn").is(normalisedIsbn);
		}

		String escaped = InstitutionSearchRepository.escape(q);
		return new Criteria().orOperator(Criteria.where("title").regex(escaped, "i"),
				Criteria.where("authors").regex(escaped, "i"), Criteria.where("subjects").regex(escaped, "i"));
	}

	private boolean hasOwnMongo(Publisher publisher) {
		return publisher.getMongoUri() != null && !publisher.getMongoUri().isBlank();
	}

	private MongoTemplate templateFor(Publisher publisher) {
		return new MongoTemplate(publisherMongoClients.factoryFor(publisher.getId(), publisher.getMongoUri()),
				mongoConverter);
	}

}
