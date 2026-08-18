package com.tf.reader.catalogue.service;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Counts, roughly, how many books an institution can currently reach — open access titles plus
 * anything covered by one of its active entitlements. Meant for a quick summary number on a
 * screen, not for deciding whether any specific book may be opened.
 */
@Service
public class InstitutionCatalogueSummaryService {

    private final CatalogueItemRepository catalogueItems;
    private final EntitlementRepository entitlements;

    public InstitutionCatalogueSummaryService(
            CatalogueItemRepository catalogueItems, EntitlementRepository entitlements) {
        this.catalogueItems = catalogueItems;
        this.entitlements = entitlements;
    }

    public long countAccessibleItems(String institutionId) {
        Set<String> reachable = new HashSet<>();

        addOpenAccessItems(reachable);

        List<Entitlement> active =
                entitlements.findByInstitutionIdAndStatus(institutionId, EntitlementStatus.ACTIVE);
        for (Entitlement e : active) {
            addItemsCoveredBy(e, reachable);
        }

        return reachable.size();
    }

    /** Open access titles need no entitlement, so every institution can reach them. */
    private void addOpenAccessItems(Set<String> reachable) {
        catalogueItems
                .findByAccessTierAndStatus(AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED)
                .stream()
                .filter(this::isReady)
                .forEach(item -> reachable.add(item.getId()));
    }

    private void addItemsCoveredBy(Entitlement e, Set<String> reachable) {
        switch (e.getScopeType()) {
            case COLLECTION -> catalogueItems
                    .findByCollectionIdsAndStatusAndContentState(
                            e.getScopeId(), ItemStatus.PUBLISHED, ContentState.READY)
                    .forEach(item -> reachable.add(item.getId()));

            case PUBLISHER -> catalogueItems
                    .findByPublisherIdAndStatus(e.getScopeId(), ItemStatus.PUBLISHED)
                    .stream()
                    .filter(this::isReady)
                    .forEach(item -> reachable.add(item.getId()));

            case ITEM -> catalogueItems
                    .findById(e.getScopeId())
                    .filter(this::isReady)
                    .filter(item -> item.getStatus() == ItemStatus.PUBLISHED)
                    .ifPresent(item -> reachable.add(item.getId()));
        }
    }

    private boolean isReady(CatalogueItem item) {
        return item.getContentState() == ContentState.READY;
    }
}
