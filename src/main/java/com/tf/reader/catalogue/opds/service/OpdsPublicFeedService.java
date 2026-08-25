package com.tf.reader.catalogue.opds.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.opds.dto.OpdsFeedMetadata;
import com.tf.reader.catalogue.opds.dto.OpdsLink;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.catalogue.service.CatalogueUrlBuilder;
import com.tf.reader.common.page.PageQuery;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class OpdsPublicFeedService {

    private static final String OPDS_MEDIA_TYPE = "application/opds+json";
    private static final String FEED_TITLE = "Open access catalogue";
    private static final EntitlementDecision OPEN_ACCESS_DECISION =
            new EntitlementDecision(true, AccessLevel.OPEN_ACCESS, null, null, 0, null, null);

    private final CatalogueItemRepository catalogueItemRepository;
    private final PublisherRepository publisherRepository;
    private final OpdsPublicationMapper publicationMapper;
    private final CatalogueUrlBuilder catalogueUrlBuilder;

    public OpdsPublicationFeed catalogueFeed(PageQuery page) {
        List<CatalogueItem> items = catalogueItemRepository.findByAccessTierAndStatusAndContentState(
                AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED, ContentState.READY,
                Sort.by(Sort.Direction.DESC, "publishedAt"));

        int from = Math.min(page.page() * page.size(), items.size());
        int to = Math.min(from + page.size(), items.size());
        List<CatalogueItem> pageItems = items.subList(from, to);

        Map<String, Publisher> publishersById = publisherRepository
                .findAllById(pageItems.stream().map(CatalogueItem::getPublisherId).distinct().toList()).stream()
                .collect(Collectors.toMap(Publisher::getId, p -> p));
        List<OpdsPublication> publications = pageItems.stream()
                .map(item -> publicationMapper.toPublicationWithSelfHref(item, OPEN_ACCESS_DECISION,
                        catalogueUrlBuilder.publicPublicationUrlFor(item.getId()), publishersById))
                .toList();

        OpdsFeedMetadata metadata = new OpdsFeedMetadata(FEED_TITLE, items.size(), page.size(), page.page(), null);
        List<OpdsLink> links = pageLinks(page, to < items.size());

        if (publications.isEmpty()) {
            OpdsLink back = new OpdsLink("subsection", catalogueUrlBuilder.publicCatalogueUrlFor(), OPDS_MEDIA_TYPE,
                    "Back to catalogue");
            return new OpdsPublicationFeed(metadata, links, null, List.of(back));
        }
        return new OpdsPublicationFeed(metadata, links, publications, null);
    }

    private List<OpdsLink> pageLinks(PageQuery page, boolean hasNext) {
        List<OpdsLink> links = new ArrayList<>();
        links.add(new OpdsLink("self", pageUrl(page.page(), page.size()), OPDS_MEDIA_TYPE));
        if (hasNext) {
            links.add(new OpdsLink("next", pageUrl(page.page() + 1, page.size()), OPDS_MEDIA_TYPE));
        }
        return links;
    }

    private String pageUrl(int pageNumber, int size) {
        return catalogueUrlBuilder.publicCatalogueUrlFor() + "?page=" + pageNumber + "&size=" + size;
    }
}
