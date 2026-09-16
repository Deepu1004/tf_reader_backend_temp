package com.tf.reader.catalogue.repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.common.mongo.PublisherMongoClients;

/**
 * The anonymous public catalogue's search - always cross-publisher by design, no
 * {@code publisherId} parameter to scope by. Fans out across the shared database and every
 * publisher's own Mongo, merges, re-sorts by title in memory, and paginates over the merged list -
 * same approach as {@code CatalogueItemSearchRepository}'s unscoped admin search.
 */
@Repository
public class CatalogueItemPublicSearchRepository {

    private static final Pattern ISBN_SHAPED = Pattern.compile("^(97[89])?[0-9]{9}[0-9X]$");

    private final MongoTemplate mongo;
    private final PublisherRepository publisherRepository;
    private final PublisherMongoClients publisherMongoClients;
    private final MongoConverter mongoConverter;

    public CatalogueItemPublicSearchRepository(MongoTemplate mongo, PublisherRepository publisherRepository,
            PublisherMongoClients publisherMongoClients, MongoConverter mongoConverter) {
        this.mongo = mongo;
        this.publisherRepository = publisherRepository;
        this.publisherMongoClients = publisherMongoClients;
        this.mongoConverter = mongoConverter;
    }

    public record Results(List<CatalogueItem> items, long total) {
    }

    public Results search(String q, ContentType contentTypeFilter, AccessTier accessTierFilter, int page, int size) {
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("status").is(ItemStatus.PUBLISHED).and("contentState").is(ContentState.READY));
        if (contentTypeFilter != null) {
            parts.add(Criteria.where("contentType").is(contentTypeFilter));
        }
        if (accessTierFilter != null) {
            parts.add(Criteria.where("accessTier").is(accessTierFilter));
        }
        parts.add(qCriteria(q.trim()));

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
        return new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
    }

    private Criteria qCriteria(String q) {
        String normalisedIsbn = q.replaceAll("[\\s-]", "").toUpperCase();
        if (ISBN_SHAPED.matcher(normalisedIsbn).matches()) {
            return Criteria.where("isbn").is(normalisedIsbn);
        }

        String escaped = InstitutionSearchRepository.escape(q);
        return new Criteria().orOperator(Criteria.where("title").regex(escaped, "i"),
                Criteria.where("authors").regex(escaped, "i"), Criteria.where("subjects").regex(escaped, "i"),
                Criteria.where("description").regex(escaped, "i"));
    }

    private MongoTemplate templateFor(Publisher publisher) {
        return new MongoTemplate(publisherMongoClients.factoryFor(publisher.getId(), publisher.getMongoUri()),
                mongoConverter);
    }
}
