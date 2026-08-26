package com.tf.reader.catalogue.opds.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.opds.dto.OpdsFeedMetadata;
import com.tf.reader.catalogue.opds.dto.OpdsLink;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.repository.InstitutionSearchRepository;
import com.tf.reader.catalogue.service.CatalogueUrlBuilder;
import com.tf.reader.common.page.PageQuery;

import lombok.RequiredArgsConstructor;

/** Backs {@code GET .../search}: metadata only (title, authors, subjects, description,
 * ISBN), never the in-file text index that runs on the device. Entitlement filtering is the
 * same {@link OpdsEntitlementFilter} the root and group feeds use, so a result the caller
 * cannot open is never returned. */
@Component
@RequiredArgsConstructor
class OpdsSearchQuery {

    // Stripped of hyphens/whitespace: ISBN-10 or ISBN-13, matched exactly rather than by
    // regex - "9780367211745" and "978-0-367-21174-5" must find the same book.
    private static final Pattern ISBN_SHAPED = Pattern.compile("^(97[89])?[0-9]{9}[0-9X]$");
    private static final String OPDS_MEDIA_TYPE = "application/opds+json";

    private final MongoTemplate mongo;
    private final OpdsEntitlementFilter entitlementFilter;
    private final CatalogueUrlBuilder catalogueUrlBuilder;

    OpdsPublicationFeed search(Institution institution, SubjectRef subject, String queryText, PageQuery page,
            ContentType contentTypeFilter, AccessTier accessTierFilter) {
        String institutionId = institution.getId();
        List<CatalogueItem> candidates = mongo.find(buildQuery(queryText, contentTypeFilter, accessTierFilter),
                CatalogueItem.class);
        List<OpdsPublication> entitled = entitlementFilter.mapEntitled(candidates, institutionId, subject);

        int from = Math.min(page.page() * page.size(), entitled.size());
        int to = Math.min(from + page.size(), entitled.size());
        List<OpdsPublication> pageItems = entitled.subList(from, to);

        OpdsFeedMetadata metadata = new OpdsFeedMetadata("Search: " + queryText, entitled.size(), page.size(),
                page.page(), null);
        List<OpdsLink> links = pageLinks(institutionId, queryText, page, contentTypeFilter, accessTierFilter,
                to < entitled.size());

        if (pageItems.isEmpty()) {
            OpdsLink back = new OpdsLink("subsection", catalogueUrlBuilder.catalogueUrlFor(institutionId),
                    OPDS_MEDIA_TYPE, "Browse the full catalogue");
            return new OpdsPublicationFeed(metadata, links, null, List.of(back));
        }
        return new OpdsPublicationFeed(metadata, links, pageItems, null);
    }

    private Query buildQuery(String queryText, ContentType contentTypeFilter, AccessTier accessTierFilter) {
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("status").is(ItemStatus.PUBLISHED));
        parts.add(Criteria.where("contentState").is(ContentState.READY));
        if (contentTypeFilter != null) {
            parts.add(Criteria.where("contentType").is(contentTypeFilter));
        }
        if (accessTierFilter != null) {
            parts.add(Criteria.where("accessTier").is(accessTierFilter));
        }
        parts.add(metadataCriteria(queryText));
        return new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.ASC, "title"));
    }

    private Criteria metadataCriteria(String queryText) {
        String normalisedIsbn = queryText.replaceAll("[\\s-]", "").toUpperCase();
        if (ISBN_SHAPED.matcher(normalisedIsbn).matches()) {
            return Criteria.where("isbn").is(normalisedIsbn);
        }
        String escaped = InstitutionSearchRepository.escape(queryText);
        return new Criteria().orOperator(
                Criteria.where("title").regex(escaped, "i"),
                Criteria.where("authors").regex(escaped, "i"),
                Criteria.where("subjects").regex(escaped, "i"),
                Criteria.where("description").regex(escaped, "i"));
    }

    // self/next must carry every active filter, not just page/size - see the identical
    // comment on OpdsCatalogueQuery.pageLinks, this is the same rule for search.
    private List<OpdsLink> pageLinks(String institutionId, String queryText, PageQuery page,
            ContentType contentTypeFilter, AccessTier accessTierFilter, boolean hasNext) {
        List<OpdsLink> links = new ArrayList<>();
        links.add(new OpdsLink("self",
                searchUrl(institutionId, queryText, page.page(), page.size(), contentTypeFilter, accessTierFilter),
                OPDS_MEDIA_TYPE));
        if (hasNext) {
            links.add(new OpdsLink("next",
                    searchUrl(institutionId, queryText, page.page() + 1, page.size(), contentTypeFilter,
                            accessTierFilter),
                    OPDS_MEDIA_TYPE));
        }
        return links;
    }

    private String searchUrl(String institutionId, String queryText, int pageNumber, int size,
            ContentType contentTypeFilter, AccessTier accessTierFilter) {
        StringBuilder url = new StringBuilder(catalogueUrlBuilder.searchUrlFor(institutionId))
                .append("?query=").append(encode(queryText))
                .append("&page=").append(pageNumber)
                .append("&size=").append(size);
        if (contentTypeFilter != null) {
            url.append("&contentType=").append(contentTypeFilter);
        }
        if (accessTierFilter != null) {
            url.append("&accessTier=").append(accessTierFilter);
        }
        return url.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
