package com.tf.reader.catalogue.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;


@Repository
public class CatalogueItemPublicSearchRepository {

    private static final Pattern ISBN_SHAPED = Pattern.compile("^(97[89])?[0-9]{9}[0-9X]$");

    private final MongoTemplate mongo;

    public CatalogueItemPublicSearchRepository(MongoTemplate mongo) {
        this.mongo = mongo;
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

        Query query = new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])));
        query.with(Sort.by(Sort.Direction.ASC, "title"));

        long total = mongo.count(Query.of(query).limit(0).skip(0), CatalogueItem.class);

        query.skip((long) page * size).limit(size);
        return new Results(mongo.find(query, CatalogueItem.class), total);
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
}
