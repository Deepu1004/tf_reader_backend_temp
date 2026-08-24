package com.tf.reader.catalogue.opds.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.FeedSettings;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.opds.dto.OpdsFeedMetadata;
import com.tf.reader.catalogue.opds.dto.OpdsLink;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.FeedSettingsRepository;
import com.tf.reader.catalogue.service.CatalogueUrlBuilder;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageQuery;

import lombok.RequiredArgsConstructor;

/** The {@code all} group: the whole entitled catalogue, paginated and sorted - unlike a
 * curated shelf, this one never 404s and honours {@code ?sort=}. */
@Component
@RequiredArgsConstructor
class OpdsCatalogueQuery {

    static final String ALL_GROUP_ID = "all";
    private static final String OPDS_MEDIA_TYPE = "application/opds+json";

    private final CatalogueItemRepository catalogueItemRepository;
    private final FeedSettingsRepository feedSettingsRepository;
    private final OpdsEntitlementFilter entitlementFilter;
    private final CatalogueUrlBuilder catalogueUrlBuilder;

    OpdsPublicationFeed allFeed(Institution institution, SubjectRef subject, PageQuery page, String sortParam,
            ContentType contentTypeFilter, AccessTier accessTierFilter) {
        String institutionId = institution.getId();
        Sort sort = resolveSort(sortParam, institutionId);
        List<CatalogueItem> candidates = catalogueItemRepository
                .findByStatusAndContentState(ItemStatus.PUBLISHED, ContentState.READY, sort).stream()
                .filter(item -> contentTypeFilter == null || item.getContentType() == contentTypeFilter)
                .filter(item -> accessTierFilter == null || item.getAccessTier() == accessTierFilter)
                .toList();
        List<OpdsPublication> entitled = entitlementFilter.mapEntitled(candidates, institutionId, subject);

        int from = Math.min(page.page() * page.size(), entitled.size());
        int to = Math.min(from + page.size(), entitled.size());
        List<OpdsPublication> pageItems = entitled.subList(from, to);

        OpdsFeedMetadata metadata = new OpdsFeedMetadata(institution.getName(), entitled.size(), page.size(),
                page.page(), institution.getUpdatedAt());
        List<OpdsLink> links = pageLinks(institutionId, page, sortParam, contentTypeFilter, accessTierFilter,
                to < entitled.size());

        if (pageItems.isEmpty()) {
            OpdsLink back = new OpdsLink("subsection", catalogueUrlBuilder.catalogueUrlFor(institutionId),
                    OPDS_MEDIA_TYPE, "Back to catalogue");
            return new OpdsPublicationFeed(metadata, links, null, List.of(back));
        }
        return new OpdsPublicationFeed(metadata, links, pageItems, null);
    }

    // self/next must carry every active filter, not just page/size - otherwise following
    // `next` silently changes sort or drops a contentType/accessTier filter mid-browse.
    private List<OpdsLink> pageLinks(String institutionId, PageQuery page, String sortParam,
            ContentType contentTypeFilter, AccessTier accessTierFilter, boolean hasNext) {
        List<OpdsLink> links = new ArrayList<>();
        links.add(new OpdsLink("self",
                pageUrl(institutionId, page.page(), page.size(), sortParam, contentTypeFilter, accessTierFilter),
                OPDS_MEDIA_TYPE));
        if (hasNext) {
            links.add(new OpdsLink("next",
                    pageUrl(institutionId, page.page() + 1, page.size(), sortParam, contentTypeFilter, accessTierFilter),
                    OPDS_MEDIA_TYPE));
        }
        links.add(new OpdsLink("search", catalogueUrlBuilder.searchUrlTemplateFor(institutionId), OPDS_MEDIA_TYPE,
                "Search this catalogue", true, null));
        return links;
    }

    private String pageUrl(String institutionId, int pageNumber, int size, String sortParam,
            ContentType contentTypeFilter, AccessTier accessTierFilter) {
        StringBuilder url = new StringBuilder(catalogueUrlBuilder.groupUrlFor(institutionId, ALL_GROUP_ID))
                .append("?page=").append(pageNumber)
                .append("&size=").append(size);
        if (sortParam != null && !sortParam.isBlank()) {
            url.append("&sort=").append(encode(sortParam));
        }
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

    private Sort resolveSort(String sortParam, String institutionId) {
        String effective = sortParam != null && !sortParam.isBlank()
                ? sortParam
                : feedSettingsRepository.findByInstitutionId(institutionId)
                        .map(FeedSettings::getDefaultSort)
                        .filter(s -> !s.isBlank())
                        .orElse("publishedAt.desc");
        return switch (effective) {
            case "publishedAt.desc" -> Sort.by(Sort.Direction.DESC, "publishedAt");
            case "publishedAt.asc" -> Sort.by(Sort.Direction.ASC, "publishedAt");
            case "title.asc" -> Sort.by(Sort.Direction.ASC, "title");
            case "title.desc" -> Sort.by(Sort.Direction.DESC, "title");
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown sort: " + effective);
        };
    }
}
