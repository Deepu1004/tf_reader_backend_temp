package com.tf.reader.catalogue.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.InstitutionLookup;
import com.tf.reader.catalogue.api.InstitutionRef;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntitlementQueryImplTest {

    private static final SubjectRef SUBJECT = new SubjectRef("u_88", "inst_7f3");

    private CatalogueItemRepository catalogueItemRepository;
    private EntitlementRepository entitlementRepository;
    private InstitutionLookup institutionLookup;
    private EntitlementQuery query;

    @BeforeEach
    void setUp() {
        catalogueItemRepository = mock(CatalogueItemRepository.class);
        entitlementRepository = mock(EntitlementRepository.class);
        institutionLookup = mock(InstitutionLookup.class);
        query = new EntitlementQueryImpl(catalogueItemRepository, entitlementRepository, institutionLookup);

        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(institutionLookup.find("inst_7f3")).thenReturn(Optional.of(new InstitutionRef("inst_7f3", "Imperial")));
    }

    @Test
    void allowsAccessWhenAnActiveCollectionGrantCoversTheItem() {
        // ELITE, not the readyItem default of SUBSCRIPTION: this test is specifically about a
        // copy-limited grant, and only ELITE is copy limited by nature.
        CatalogueItem item = readyItem("item_c25", List.of("col_1"));
        item.setAccessTier(AccessTier.ELITE);
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        Entitlement grant = entitlement("ent_1", ScopeType.COLLECTION, "col_1", 3, 21,
                LocalDate.now().plusDays(30));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.COLLECTION), eq("col_1")))
                .thenReturn(Optional.of(grant));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isTrue();
        assertThat(decision.entitlementId()).isEqualTo("ent_1");
        assertThat(decision.copies()).isEqualTo(3);
        assertThat(decision.loanPeriodDays()).isEqualTo(21);
        assertThat(decision.accessLevel()).isEqualTo(AccessLevel.ENTITLED_CONCURRENT);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void aSubscriptionItemIsNeverCopyLimitedEvenWhenTheMatchedGrantHasCopies() {
        // The same grant can cover a whole publisher, ELITE and SUBSCRIPTION titles alike. A
        // SUBSCRIPTION book must not inherit a concurrency limit meant for the ELITE titles
        // sharing that grant - the tier decides, not the number on the row.
        CatalogueItem item = readyItem("item_c25", List.of("col_1"));
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        Entitlement grant = entitlement("ent_1", ScopeType.COLLECTION, "col_1", 3, 21,
                LocalDate.now().plusDays(30));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.COLLECTION), eq("col_1")))
                .thenReturn(Optional.of(grant));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isTrue();
        assertThat(decision.copies()).isNull();
        assertThat(decision.accessLevel()).isEqualTo(AccessLevel.ENTITLED_UNLIMITED);
    }

    @Test
    void deniesWithNoEntitlementWhenNothingCoversTheItem() {
        CatalogueItem item = readyItem("item_c25", List.of());
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isFalse();
        assertThat(decision.reason()).isEqualTo(DenyReason.NO_ENTITLEMENT);
    }

    @Test
    void deniesWithNoEntitlementWhenTheOnlyGrantHasExpired() {
        CatalogueItem item = readyItem("item_c25", List.of("col_1"));
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        Entitlement expired = entitlement("ent_1", ScopeType.COLLECTION, "col_1", 3, 21,
                LocalDate.now().minusDays(1));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.COLLECTION), eq("col_1")))
                .thenReturn(Optional.of(expired));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isFalse();
        assertThat(decision.reason()).isEqualTo(DenyReason.NO_ENTITLEMENT);
    }

    @Test
    void deniesWithContentNotReadyWhenTheItemIsNotPublished() {
        CatalogueItem item = readyItem("item_c25", List.of());
        item.setStatus(ItemStatus.DRAFT);
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isFalse();
        assertThat(decision.reason()).isEqualTo(DenyReason.CONTENT_NOT_READY);
    }

    @Test
    void rejectsAMissingItemId() {
        assertThatIllegalArgumentException().isThrownBy(() -> query.check(SUBJECT, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> query.check(SUBJECT, null));
    }

    @Test
    void deniesWithNotFoundWhenTheInstitutionIsSuspended() {
        // InstitutionLookup itself collapses "suspended" and "unknown" into an empty Optional -
        // check() never sees the difference, so this test only needs to stub the empty case.
        when(institutionLookup.find("inst_7f3")).thenReturn(Optional.empty());

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isFalse();
        assertThat(decision.reason()).isEqualTo(DenyReason.NOT_FOUND);
        verify(catalogueItemRepository, never()).findById(any());
    }

    @Test
    void deniesWithNotFoundWhenTheInstitutionIsUnknown() {
        when(institutionLookup.find("inst_7f3")).thenReturn(Optional.empty());

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitled()).isFalse();
        assertThat(decision.reason()).isEqualTo(DenyReason.NOT_FOUND);
        verify(catalogueItemRepository, never()).findById(any());
    }

    @Test
    void anItemGrantWinsOverAMorePermissiveCollectionGrant() {
        CatalogueItem item = readyItem("item_c25", List.of("col_1"));
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        Entitlement itemGrant = entitlement("ent_item", ScopeType.ITEM, "item_c25", 1, 14,
                LocalDate.now().plusDays(30));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.ITEM), eq("item_c25")))
                .thenReturn(Optional.of(itemGrant));

        Entitlement collectionGrant = entitlement("ent_collection", ScopeType.COLLECTION, "col_1", null, 21,
                LocalDate.now().plusDays(30));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.COLLECTION), eq("col_1")))
                .thenReturn(Optional.of(collectionGrant));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitlementId()).isEqualTo("ent_item");
        assertThat(decision.loanPeriodDays()).isEqualTo(14);
    }

    @Test
    void theMorePermissiveOfTwoCollectionGrantsWinsWhenAnItemBelongsToBoth() {
        CatalogueItem item = readyItem("item_c25", List.of("col_1", "col_2"));
        when(catalogueItemRepository.findById("item_c25")).thenReturn(Optional.of(item));

        Entitlement limited = entitlement("ent_limited", ScopeType.COLLECTION, "col_1", 2, 14,
                LocalDate.now().plusDays(30));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.COLLECTION), eq("col_1")))
                .thenReturn(Optional.of(limited));

        Entitlement unlimited = entitlement("ent_unlimited", ScopeType.COLLECTION, "col_2", null, 14,
                LocalDate.now().plusDays(30));
        when(entitlementRepository.findByInstitutionIdAndScopeTypeAndScopeId(
                eq("inst_7f3"), eq(ScopeType.COLLECTION), eq("col_2")))
                .thenReturn(Optional.of(unlimited));

        EntitlementDecision decision = query.check(SUBJECT, "item_c25");

        assertThat(decision.entitlementId()).isEqualTo("ent_unlimited");
    }

    private CatalogueItem readyItem(String id, List<String> collectionIds) {
        CatalogueItem item = new CatalogueItem();
        item.setId(id);
        item.setPublisherId("pub_1");
        item.setCollectionIds(collectionIds);
        item.setAccessTier(AccessTier.SUBSCRIPTION);
        item.setStatus(ItemStatus.PUBLISHED);
        item.setContentState(ContentState.READY);
        return item;
    }

    private Entitlement entitlement(String id, ScopeType scopeType, String scopeId, Integer copies,
            Integer loanPeriodDays, LocalDate validTo) {
        Entitlement entitlement = new Entitlement();
        entitlement.setId(id);
        entitlement.setInstitutionId("inst_7f3");
        entitlement.setScopeType(scopeType);
        entitlement.setScopeId(scopeId);
        entitlement.setCopies(copies);
        entitlement.setLoanPeriodDays(loanPeriodDays);
        entitlement.setValidFrom(LocalDate.now().minusDays(1));
        entitlement.setValidTo(validTo);
        entitlement.setStatus(EntitlementStatus.ACTIVE);
        return entitlement;
    }
}
