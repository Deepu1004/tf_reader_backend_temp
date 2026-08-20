package com.tf.reader.catalogue;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.service.InstitutionCatalogueSummaryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Checks the accessible-item count against the cases most likely to get it wrong. */
class InstitutionCatalogueSummaryServiceTest {

    private CatalogueItemRepository items;
    private EntitlementRepository entitlements;
    private InstitutionCatalogueSummaryService summary;

    @BeforeEach
    void setUp() {
        items = mock(CatalogueItemRepository.class);
        entitlements = mock(EntitlementRepository.class);
        summary = new InstitutionCatalogueSummaryService(items, entitlements);

        // No open access items unless a test says otherwise.
        when(items.findByAccessTierAndStatus(AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("two overlapping grants count the same book once, not twice")
    void overlappingGrantsDoNotDoubleCount() {
        // One book, covered by both a collection grant and a publisher grant.
        CatalogueItem item42 = readyItem("item_42", "col_law2024");

        when(entitlements.findByInstitutionIdAndStatus("inst_7f3", EntitlementStatus.ACTIVE))
                .thenReturn(List.of(
                        entitlement(ScopeType.COLLECTION, "col_law2024"),
                        entitlement(ScopeType.PUBLISHER, "pub_rtlg")));
        when(items.findByCollectionIdsAndStatusAndContentState(
                        "col_law2024", ItemStatus.PUBLISHED, ContentState.READY))
                .thenReturn(List.of(item42));
        when(items.findByPublisherIdAndStatus("pub_rtlg", ItemStatus.PUBLISHED))
                .thenReturn(List.of(item42));

        assertThat(summary.countAccessibleItems("inst_7f3")).isEqualTo(1);
    }

    @Test
    @DisplayName("zero entitlements still counts open access books")
    void zeroEntitlementsStillCountsOpenAccess() {
        // No grants at all, but open access needs none.
        when(entitlements.findByInstitutionIdAndStatus("inst_leeds", EntitlementStatus.ACTIVE))
                .thenReturn(List.of());
        when(items.findByAccessTierAndStatus(AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED))
                .thenReturn(List.of(readyItem("item_ab6", null), readyItem("item_oa9", null)));

        assertThat(summary.countAccessibleItems("inst_leeds")).isEqualTo(2);
    }

    @Test
    @DisplayName("a QUEUED or FAILED book inside a granted scope is never counted")
    void notReadyItemsAreExcludedEvenInsideAGrantedScope() {
        CatalogueItem queued = new CatalogueItem();
        queued.setId("item_q7");
        queued.setStatus(ItemStatus.PUBLISHED);
        queued.setContentState(ContentState.QUEUED); // published but not ready

        when(entitlements.findByInstitutionIdAndStatus("inst_7f3", EntitlementStatus.ACTIVE))
                .thenReturn(List.of(entitlement(ScopeType.COLLECTION, "col_law2024")));
        // A queued book never comes back from the lookup, so there is nothing to add.
        when(items.findByCollectionIdsAndStatusAndContentState(
                        "col_law2024", ItemStatus.PUBLISHED, ContentState.READY))
                .thenReturn(List.of());

        assertThat(summary.countAccessibleItems("inst_7f3")).isZero();
    }

    @Test
    @DisplayName("an ITEM-scope grant on a not-ready book counts nothing")
    void itemScopeGrantOnNotReadyBookCountsNothing() {
        CatalogueItem failed = new CatalogueItem();
        failed.setId("item_f3");
        failed.setStatus(ItemStatus.PUBLISHED);
        failed.setContentState(ContentState.FAILED);

        when(entitlements.findByInstitutionIdAndStatus("inst_x", EntitlementStatus.ACTIVE))
                .thenReturn(List.of(entitlement(ScopeType.ITEM, "item_f3")));
        when(items.findById("item_f3")).thenReturn(Optional.of(failed));

        assertThat(summary.countAccessibleItems("inst_x")).isZero();
    }

    private static CatalogueItem readyItem(String id, String collectionId) {
        CatalogueItem item = new CatalogueItem();
        item.setId(id);
        item.setStatus(ItemStatus.PUBLISHED);
        item.setContentState(ContentState.READY);
        item.setCollectionIds(collectionId == null ? List.of() : List.of(collectionId));
        return item;
    }

    private static Entitlement entitlement(ScopeType scopeType, String scopeId) {
        Entitlement e = new Entitlement();
        e.setScopeType(scopeType);
        e.setScopeId(scopeId);
        e.setStatus(EntitlementStatus.ACTIVE);
        return e;
    }
}