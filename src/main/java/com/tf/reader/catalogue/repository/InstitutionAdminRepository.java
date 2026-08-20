package com.tf.reader.catalogue.repository;

import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.common.model.RecordStatus;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Looks up institutions for the admin console, where every status — including a suspended one —
 * must stay visible to an operator.
 */
@Repository
public class InstitutionAdminRepository {

    private final MongoTemplate mongo;

    public InstitutionAdminRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public record Results(List<Institution> items, long total) {}

    /**
     * @param q                 an optional search term, matched against the start of any word in the name
     * @param status            an optional filter; leave it out to see every status
     * @param institutionIdScope when set, restricts the result to that one institution — applied
     *                            here as a query condition, never as a post-fetch filter, so a
     *                            scoped caller's request never reads more from Mongo than they
     *                            are allowed to see
     */
    public Results search(String q, RecordStatus status, String institutionIdScope, int page, int size) {
        Criteria criteria = new Criteria();

        if (institutionIdScope != null) {
            criteria = criteria.and("_id").is(institutionIdScope);
        }
        if (status != null) {
            criteria = criteria.and("status").is(status);
        }
        if (q != null) {
            criteria = criteria.and("name").regex(InstitutionSearchRepository.prefixPattern(q), "i");
        }

        Query query = new Query(criteria);
        query.with(Sort.by(Sort.Direction.ASC, "name"));

        long total = mongo.count(Query.of(query).limit(0).skip(0), Institution.class);

        query.skip((long) page * size).limit(size);
        return new Results(mongo.find(query, Institution.class), total);
    }

    public Optional<Institution> findById(String institutionId) {
        return Optional.ofNullable(mongo.findById(institutionId, Institution.class));
    }
}
