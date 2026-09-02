package com.tf.reader.catalogue.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
class EntitlementQueryImpl implements EntitlementQuery {

    private final CatalogueItemRepository catalogueItemRepository;
    private final EntitlementRepository entitlementRepository;

    @Override
    public EntitlementDecision check(SubjectRef subject, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }

        Optional<CatalogueItem> maybeItem = catalogueItemRepository.findById(itemId);
        if (maybeItem.isEmpty()) {
            return denied(DenyReason.NOT_FOUND);
        }

        CatalogueItem item = maybeItem.get();
        if (item.getStatus() != ItemStatus.PUBLISHED || item.getContentState() != ContentState.READY) {
            return denied(DenyReason.CONTENT_NOT_READY);
        }

        // Open access was never something an institution had to buy, so it needs no grant at
        // all - this must run before the grant lookup below, not after, or a book with this
        // tier and zero specific entitlements is wrongly denied as NO_ENTITLEMENT.
        if (item.getAccessTier() == AccessTier.OPEN_ACCESS) {
            return new EntitlementDecision(true, AccessLevel.OPEN_ACCESS, null, null, 0, null, null);
        }

        Entitlement grant = mostPermissiveActiveGrant(subject.institutionId(), item);
        if (grant == null) {
            return denied(DenyReason.NO_ENTITLEMENT);
        }

        // A grant's copies count is a term of what the institution bought at that scope, and one
        // grant (a whole publisher, say) can cover books of more than one tier. Only ELITE is
        // copy limited by nature, so a SUBSCRIPTION book sharing that grant must never inherit a
        // concurrency limit meant for the ELITE titles alongside it - regardless of what copies
        // says on the matched row.
        boolean copyLimited = item.getAccessTier() == AccessTier.ELITE;

        return new EntitlementDecision(
                true,
                accessLevelFor(copyLimited, grant),
                grant.getId(),
                copyLimited ? grant.getCopies() : null,
                grant.getLoanPeriodDays(),
                grant.getValidTo() == null ? null : grant.getValidTo().atStartOfDay(ZoneOffset.UTC).toInstant(),
                null
        );
    }

    // Scope specificity, narrowest first. An ITEM grant always wins over a COLLECTION grant,
    // which always wins over a PUBLISHER grant, no matter what each one's copies says - a
    // narrower entitlement is a deliberate, more specific purchase and must not be shadowed by
    // a wider one that happens to look more generous.
    private Entitlement mostPermissiveActiveGrant(String institutionId, CatalogueItem item) {
        Entitlement itemGrant = activeGrant(entitlementRepository
                .findByInstitutionIdAndScopeTypeAndScopeId(institutionId, ScopeType.ITEM, item.getId()));
        if (itemGrant != null) {
            return itemGrant;
        }

        List<String> collectionIds = item.getCollectionIds() == null ? List.of() : item.getCollectionIds();
        List<Entitlement> collectionGrants = new ArrayList<>();
        for (String collectionId : collectionIds) {
            Entitlement grant = activeGrant(entitlementRepository
                    .findByInstitutionIdAndScopeTypeAndScopeId(institutionId, ScopeType.COLLECTION, collectionId));
            if (grant != null) {
                collectionGrants.add(grant);
            }
        }
        Entitlement bestCollectionGrant = mostPermissiveOf(collectionGrants);
        if (bestCollectionGrant != null) {
            return bestCollectionGrant;
        }

        return activeGrant(entitlementRepository
                .findByInstitutionIdAndScopeTypeAndScopeId(institutionId, ScopeType.PUBLISHER, item.getPublisherId()));
    }

    private Entitlement activeGrant(Optional<Entitlement> maybeGrant) {
        if (maybeGrant.isEmpty()) {
            return null;
        }
        Entitlement grant = maybeGrant.get();
        LocalDate today = LocalDate.now();
        if (grant.getStatus() != EntitlementStatus.ACTIVE) {
            return null;
        }
        if (grant.getValidTo() != null && grant.getValidTo().isBefore(today)) {
            return null;
        }
        return grant;
    }

    private Entitlement mostPermissiveOf(List<Entitlement> candidates) {
        Entitlement best = null;
        for (Entitlement candidate : candidates) {
            if (best == null || isMorePermissive(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private boolean isMorePermissive(Entitlement candidate, Entitlement current) {
        if (candidate.getCopies() == null) {
            return current.getCopies() != null;
        }
        if (current.getCopies() == null) {
            return false;
        }
        return candidate.getCopies() > current.getCopies();
    }

    // OPEN_ACCESS is handled earlier in check(), before a grant is looked up, so this is only
    // ever reached for a real grant now - never for an open access item.
    private AccessLevel accessLevelFor(boolean copyLimited, Entitlement grant) {
        if (!copyLimited) {
            return AccessLevel.ENTITLED_UNLIMITED;
        }
        return grant.getCopies() == null ? AccessLevel.ENTITLED_UNLIMITED : AccessLevel.ENTITLED_CONCURRENT;
    }

    private EntitlementDecision denied(DenyReason reason) {
        return new EntitlementDecision(false, null, null, null, 0, null, reason);
    }
}
