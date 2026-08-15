package com.tf.reader.catalogue.repository;

import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.common.model.RecordStatus;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The read side of the two public institution endpoints.
 *
 * <p>Separate from Person B's {@code InstitutionRepository} on purpose: this exists only to serve
 * these endpoints, so it is ours to own and needs no edit to a file four people depend on.
 *
 * <p>The query is dynamic, so it cannot be a derived finder: {@code q} and {@code country} are each
 * optional, and {@code q} is a case-insensitive prefix match.
 *
 * <p><b>Why not B's {@code findAllBy(TextCriteria)}.</b> A MongoDB text index matches whole words.
 * The contract's own example is {@code q=imp} matching "Imperial College London", which {@code $text}
 * cannot do. So the {@code @TextIndexed} fields on the entity are not used here.
 */
@Repository
public class InstitutionSearchRepository {

    /** Characters that mean something to PCRE2 and must not, coming from a search box. */
    private static final String REGEX_METACHARACTERS = "\\^$.|?*+()[]{}";

    private final MongoTemplate mongo;

    public InstitutionSearchRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /** One page of matches, plus the total that matched. */
    public record Results(List<Institution> items, long total) {}

    /**
     * @param q       trimmed, may be null. Prefix match on {@code name}, per the contract
     * @param country trimmed, may be null. Exact match, case insensitive
     */
    public Results search(String q, String country, int page, int size) {
        // Equality with ACTIVE, not "not RETIRED", so a status added next month is excluded by
        // default rather than becoming public.
        Criteria criteria = Criteria.where("status").is(RecordStatus.ACTIVE);

        if (country != null) {
            // Case insensitive, still exact. Upper-casing would work for codes like UK and break the
            // moment somebody stores "United Kingdom", which the contract also allows.
            criteria = criteria.and("country").regex("^" + escape(country) + "$", "i");
        }

        if (q != null) {
            criteria = criteria.and("name").regex(prefixPattern(q), "i");
        }

        Query query = new Query(criteria);

        // The sort is part of the contract, not an implementation detail: the picker is an
        // alphabetical list a human scans. No sort parameter, so nobody can ask for an unindexed one.
        query.with(Sort.by(Sort.Direction.ASC, "name"));

        long total = mongo.count(Query.of(query).limit(0).skip(0), Institution.class);

        query.skip((long) page * size).limit(size);
        return new Results(mongo.find(query, Institution.class), total);
    }

    /**
     * A single institution, but only if it is ACTIVE.
     *
     * <p>The status filter is in the query, not a check after the read, so there is no path where a
     * caller gets a suspended institution because someone forgot the second line. Empty means
     * "unknown or inactive" and the caller cannot tell which, which is the point.
     */
    public Optional<Institution> findActiveById(String institutionId) {
        Query query =
                new Query(
                        Criteria.where("_id").is(institutionId).and("status").is(RecordStatus.ACTIVE));
        return Optional.ofNullable(mongo.findOne(query, Institution.class));
    }

    /**
     * {@code impe} becomes {@code (^|\s)impe}: matches the start of any word, so "college" finds
     * "Imperial College London" but "mperial" finds nothing. Anchoring keeps results recognisable;
     * an unanchored match returns "London" for what reads like a typo.
     */
    public static String prefixPattern(String term) {
        return "(^|\\s)" + escape(term);
    }

    /**
     * Escapes each metacharacter by hand rather than using {@code Pattern.quote}.
     *
     * <p>{@code Pattern.quote} emits a Java {@code \Q...\E} block for an engine running on the
     * server. It happens to work in PCRE2, but then a user typing {@code \E} changes how the rest of
     * their own input is parsed, decided by a dialect we did not choose and do not test.
     */
    public static String escape(String term) {
        StringBuilder out = new StringBuilder(term.length() + 8);
        for (char ch : term.toCharArray()) {
            if (REGEX_METACHARACTERS.indexOf(ch) >= 0) {
                out.append('\\');
            }
            out.append(ch);
        }
        return out.toString();
    }

    /** Exposed so the test and the implementation cannot drift on which characters are escaped. */
    public static List<Character> metacharacters() {
        List<Character> chars = new ArrayList<>();
        for (char ch : REGEX_METACHARACTERS.toCharArray()) {
            chars.add(ch);
        }
        return chars;
    }
}