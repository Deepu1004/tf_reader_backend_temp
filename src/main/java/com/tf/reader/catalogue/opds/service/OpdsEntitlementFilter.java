package com.tf.reader.catalogue.opds.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.repository.PublisherRepository;

import lombok.RequiredArgsConstructor;

/**
 * Filters candidate {@link CatalogueItem}s down to the ones this subject may see, and maps
 * the survivors to {@link OpdsPublication}. Shared by the root feed, a curated shelf and the
 * {@code all} group - every OPDS listing goes through here.
 */
@Component
@RequiredArgsConstructor
class OpdsEntitlementFilter {

    private final EntitlementQuery entitlementQuery;
    private final OpdsPublicationMapper publicationMapper;
    private final PublisherRepository publisherRepository;

    List<OpdsPublication> mapEntitled(List<CatalogueItem> items, String institutionId, SubjectRef subject) {
        Map<String, Publisher> publishersById = publisherRepository
                .findAllById(items.stream().map(CatalogueItem::getPublisherId).distinct().toList()).stream()
                .collect(Collectors.toMap(Publisher::getId, p -> p));

        List<OpdsPublication> result = new ArrayList<>();
        for (CatalogueItem item : items) {
            EntitlementDecision decision = entitlementQuery.check(subject, item.getId());
            if (decision.entitled()) {
                result.add(publicationMapper.toPublication(item, decision, institutionId, publishersById));
            }
        }
        return result;
    }

    // Same entitlement pass as mapEntitled, without the publisher lookup or the mapping -
    // for the root feed's metadata.numberOfItems, which needs only a count.
    int countEntitled(List<CatalogueItem> items, SubjectRef subject) {
        int count = 0;
        for (CatalogueItem item : items) {
            if (entitlementQuery.check(subject, item.getId()).entitled()) {
                count++;
            }
        }
        return count;
    }
}
