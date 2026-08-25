package com.tf.reader.admin;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.entity.AdminStatus;
import com.tf.reader.admin.entity.AdminUser;
import com.tf.reader.admin.repository.AdminUserRepository;
import com.tf.reader.admin.service.DemoDataSeeder;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.FeedSettings;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.entity.Shelf;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.FeedSettingsRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;

import com.mongodb.client.MongoClient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mongodb.MongoDBContainer;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 8, integration level: runs a real MongoDB container to check if...
 * guards fire and the seeder works on a second run. Needs Docker.
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class DemoDataSeederIT {

    @Container static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        // Testcontainers publishes on 127.0.0.1, which is in the seeder's default allowlist. That is
        // not an accident: if the allowlist were tightened to reject it, these tests would fail loudly
        // rather than the rail being quietly weakened.
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("tnf.seed.enabled", () -> "true");
    }

    @Autowired DemoDataSeeder seeder;
    @Autowired PublisherRepository publishers;
    @Autowired BookCollectionRepository collections;
    @Autowired InstitutionRepository institutions;
    @Autowired CatalogueItemRepository items;
    @Autowired EntitlementRepository entitlements;
    @Autowired AdminUserRepository adminUsers;
    @Autowired FeedSettingsRepository feedSettings;
    @Autowired MongoClient mongoClient;
    @Autowired MongoDatabaseFactory mongoDatabaseFactory;

    // ------------------------------------------------------------------------------- the basics

    @Test
    @DisplayName("one run fills an empty database with the expected counts")
    void seedFillsAnEmptyDatabase() {
        assertCounts();
    }

    @Test
    @DisplayName("running it twice changes nothing")
    void seedIsIdempotent() throws Exception {
        seeder.run(null);
        seeder.run(null);
        assertCounts();
    }

    @Test
    @DisplayName("a local edit survives the next run")
    void seedDoesNotOverwriteLocalEdits() throws Exception {
        // This is insert-if-absent made visible. A developer who suspends a record to test something
        // must not have it silently reverted on the next restart.
        Institution imperial = institutions.findById("inst_7f3").orElseThrow();
        imperial.setStatus(RecordStatus.SUSPENDED);
        institutions.save(imperial);

        seeder.run(null);

        assertThat(institutions.findById("inst_7f3").orElseThrow().getStatus())
                .isEqualTo(RecordStatus.SUSPENDED);

        // Put it back, because the other tests share this container.
        imperial.setStatus(RecordStatus.ACTIVE);
        institutions.save(imperial);
    }

    @Test
    @DisplayName("reset restores the dataset exactly and leaves every index alone")
    void resetRestoresTheDatasetAndPreservesIndexes() throws Exception {
        DemoDataSeeder resetting = seederWithReset();

        Publisher routledge = publishers.findById("pub_rtlg").orElseThrow();
        routledge.setStatus(RecordStatus.RETIRED);
        publishers.save(routledge);

        resetting.run(null);

        assertCounts();
        assertThat(publishers.findById("pub_rtlg").orElseThrow().getStatus())
                .as("reset reapplies the dataset, unlike a normal run")
                .isEqualTo(RecordStatus.ACTIVE);

        // The reason reset deletes documents instead of dropping collections: a drop takes the indexes
        // with it, and Spring only creates them from the entity annotations at startup. Three unique
        // constraints prove it survived.
        assertUniqueIndexStillFires();
    }

    @Test
    @DisplayName("reset deletes in reverse dependency order, so the item guard never sees an orphan")
    void resetOrderRespectsTheItemGuard() throws Exception {
        // CatalogueItemPersistenceGuard rejects an item whose publisher does not exist. If reset
        // deleted publishers first and re-inserted in the same order, this would still pass; if either
        // order were reversed, the run would throw IllegalArgumentException instead.
        seederWithReset().run(null);
        assertThat(items.count()).isEqualTo(8);
        assertThat(items.findById("item_42").orElseThrow().getPublisherId()).isEqualTo("pub_rtlg");
    }

    // ------------------------------------------------------------------------ the entity shapes

    @Test
    @DisplayName("publishers keep the literal timestamps and the normalised code")
    void publishersAreSeeded() {
        Publisher routledge = publishers.findById("pub_rtlg").orElseThrow();
        assertThat(routledge.getCode()).isEqualTo("RTLG");
        assertThat(routledge.getName()).isEqualTo("Routledge");
        assertThat(routledge.getStatus()).isEqualTo(RecordStatus.ACTIVE);
        assertThat(routledge.getCreatedAt())
                .as("a literal from the dataset, never Instant.now()")
                .isEqualTo(Instant.parse("2026-08-10T09:00:00Z"));

        assertThat(publishers.findById("pub_crc").orElseThrow().getCode()).isEqualTo("CRCP");
    }

    @Test
    @DisplayName("collections are seeded under the right publishers")
    void collectionsAreSeeded() {
        assertThat(collections.findByPublisherIdAndCode("pub_rtlg", "LAW2024")).isPresent();
        assertThat(collections.findByPublisherIdAndCode("pub_crc", "ENV2024")).isPresent();
        assertThat(collections.findByPublisherIdAndCode("pub_crc", "LAW2024"))
                .as("a collection belongs to exactly one publisher")
                .isEmpty();
    }

    @Test
    @DisplayName("institutions carry both embedded objects and the enum B actually declared")
    void institutionsAreSeeded() {
        Institution imperial = institutions.findByCode("imperial").orElseThrow();
        assertThat(imperial.getId()).isEqualTo("inst_7f3");
        assertThat(imperial.getStatus()).isEqualTo(RecordStatus.ACTIVE);
        assertThat(imperial.getCatalogueVersion()).isEqualTo(1L);

        assertThat(imperial.getType().name())
                .as("ACADEMIC, not UNIVERSITY: B's InstitutionType has no UNIVERSITY")
                .isEqualTo("ACADEMIC");

        // The nested embedded objects survive the round trip. SignIn is Institution.SignIn.
        assertThat(imperial.getBranding().getPrimaryColor()).isEqualTo("#003E74");
        assertThat(imperial.getSignIn().getMethod()).isEqualTo("SAML");
        assertThat(imperial.getSignIn().getIdpHint()).isEqualTo("imperial-saml-mock");

        // The row that exists so "an inactive institution does not appear in the public list" has
        // something to prove. SUSPENDED, because RecordStatus has no INACTIVE.
        assertThat(institutions.findById("inst_leeds").orElseThrow().getStatus())
                .isEqualTo(RecordStatus.SUSPENDED);
    }

    @Test
    @DisplayName("items carry the server-only keys on the item and the assets under it")
    void itemsAreSeeded() {
        CatalogueItem elite = items.findById("item_42").orElseThrow();
        assertThat(elite.getAccessTier()).isEqualTo(AccessTier.ELITE);
        assertThat(elite.getStatus()).isEqualTo(ItemStatus.PUBLISHED);
        assertThat(elite.getContentState()).isEqualTo(ContentState.READY);
        assertThat(elite.getCollectionIds()).containsExactly("col_law2024");

        // storageKey, indexKey and wrappedBek are on the item in B's shape, not on the asset.
        assertThat(elite.getStorageKey()).startsWith("seed/");
        assertThat(elite.getWrappedBek()).isNotNull();

        CatalogueItem.Asset pdf = elite.getAssets().get(0);
        assertThat(pdf.getFormat()).isEqualTo(ContentType.PDF);
        assertThat(pdf.getSizeBytes()).isEqualTo(6_373_752L);
        assertThat(pdf.getCipherLength())
                .as("12 + sizeBytes + 16")
                .isEqualTo(6_373_752L + 28L);
        assertThat(pdf.isEncrypted()).isTrue();

        // Membership is stored once, on the book. item_env belongs to no collection and is reachable
        // only through the publisher-scope grant.
        assertThat(items.findById("item_env").orElseThrow().getCollectionIds()).isEmpty();
    }

    @Test
    @DisplayName("null cipherLength and indexTerms become 0 on the way into B's primitives")
    void nullableAssetNumbersBecomeZero() {
        // The dataset says null because "not encrypted" and "zero bytes of ciphertext" are different
        // facts. fields are primitives, so this is the one place the two representations meet, and
        // it is worth an assertion rather than a comment.
        CatalogueItem openAccess = items.findById("item_ab6").orElseThrow();
        assertThat(openAccess.getAssets().get(0).isEncrypted()).isFalse();
        assertThat(openAccess.getAssets().get(0).getCipherLength()).isZero();
        assertThat(openAccess.getWrappedBek()).as("plaintext, so no wrapped key").isNull();

        CatalogueItem audio = items.findById("item_stat").orElseThrow();
        assertThat(audio.getAccessTier())
                .as("a paid tier that is still not encrypted, because audio never is")
                .isEqualTo(AccessTier.SUBSCRIPTION);
        assertThat(audio.getAssets().get(0).isEncrypted()).isFalse();
        assertThat(audio.getAssets().get(0).isHasSearchIndex()).isFalse();
        assertThat(audio.getAssets().get(0).getIndexTerms()).isZero();
        assertThat(audio.getAssets().get(0).getIndexSkipReason()).isEqualTo("AudioNotIndexable");
    }

    @Test
    @DisplayName("the two books that must never reach a feed are present and excluded")
    void notReadyItemsAreSeededButNotRenderable() {
        CatalogueItem queued = items.findById("item_q7").orElseThrow();
        assertThat(queued.getStatus()).as("editorially published").isEqualTo(ItemStatus.PUBLISHED);
        assertThat(queued.getContentState()).as("but mechanically not ready").isEqualTo(ContentState.QUEUED);
        assertThat(queued.getAssets()).isEmpty();
        assertThat(queued.getStorageKey()).as("no assets, so no key pointing at nothing").isNull();

        CatalogueItem failed = items.findById("item_f3").orElseThrow();
        assertThat(failed.getContentState()).isEqualTo(ContentState.FAILED);
        assertThat(failed.getContentError()).contains("no extractable text layer");

        // The derived finder every feed will use returns neither of them.
        List<CatalogueItem> renderable =
                items.findByCollectionIdsAndStatusAndContentState(
                        "col_law2024", ItemStatus.PUBLISHED, ContentState.READY);
        assertThat(renderable).extracting(CatalogueItem::getId).containsExactly("item_42");
    }

    @Test
    @DisplayName("all three grants insert cleanly and express two different access models")
    void entitlementsAreSeeded() {
        // Reaching here at all is the assertion about version: a seeded @Version would have failed the
        // first insert with OptimisticLockingFailureException. B declared a plain long, so it is 0.
        assertThat(entitlements.count()).isEqualTo(3);

        Entitlement concurrent =
                entitlements
                        .findByInstitutionIdAndScopeTypeAndScopeId(
                                "inst_7f3", ScopeType.COLLECTION, "col_law2024")
                        .orElseThrow();
        assertThat(concurrent.getCopies()).as("a copy limit, so CONCURRENT").isEqualTo(2);
        assertThat(concurrent.getLoanPeriodDays()).isEqualTo(14);
        assertThat(concurrent.getVersion()).isZero();

        Entitlement unlimited =
                entitlements
                        .findByInstitutionIdAndScopeTypeAndScopeId(
                                "inst_7f3", ScopeType.PUBLISHER, "pub_rtlg")
                        .orElseThrow();
        assertThat(unlimited.getCopies()).as("null copies, so UNLIMITED").isNull();
        assertThat(unlimited.getValidTo()).as("open ended").isNull();

        // All three are Imperial's, which is what lets one feed show CONCURRENT and UNLIMITED side by
        // side exactly as the frozen day 1 fixtures do. UCL is the zero-entitlement institution.
        assertThat(entitlements.findByInstitutionIdAndStatus("inst_7f3", EntitlementStatus.ACTIVE))
                .hasSize(3);
        assertThat(entitlements.findByInstitutionIdAndStatus("inst_ucl", EntitlementStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    @DisplayName("admin users use AdminStatus and keep the pre-computed hash")
    void adminUsersAreSeeded() {
        AdminUser ops = adminUsers.findByEmail("ops@tandf.example").orElseThrow();
        assertThat(ops.getId()).isEqualTo("adm_01");
        assertThat(ops.getRole()).isEqualTo(AdminRole.SUPER_ADMIN);
        assertThat(ops.getStatus())
                .as("AdminStatus, not RecordStatus: B split it out and its third value is DISABLED")
                .isEqualTo(AdminStatus.ACTIVE);
        assertThat(ops.getPublisherId()).as("a SUPER_ADMIN is scoped to nothing").isNull();
        assertThat(ops.getInstitutionId()).isNull();

        // Pre-computed and identical on every machine, because BCrypt is salted and hashing at load
        // time would break the one property this task exists to provide.
        assertThat(ops.getPasswordHash()).startsWith("$2a$10$");

        assertThat(adminUsers.findById("adm_02").orElseThrow().getPublisherId()).isEqualTo("pub_rtlg");
        assertThat(adminUsers.findById("adm_03").orElseThrow().getInstitutionId()).isEqualTo("inst_7f3");
    }

    @Test
    @DisplayName("feed settings pass B's guard and preserve both orderings")
    void feedSettingsAreSeeded() {
        FeedSettings imperial = feedSettings.findByInstitutionId("inst_7f3").orElseThrow();
        assertThat(imperial.getId()).isEqualTo("fs_inst_7f3");
        assertThat(imperial.getFeedTitle()).isEqualTo("Imperial College Library");
        assertThat(imperial.getPageSize()).isEqualTo(20);

        // Reaching here means FeedSettingsPersistenceGuard accepted the document, which is itself the
        // exactly-three-shelves assertion. These check the two orderings the feed must preserve.
        assertThat(imperial.getShelves()).hasSize(3);
        assertThat(imperial.getShelves()).extracting(Shelf::getId)
                .containsExactly("shelf_1", "shelf_2", "shelf_3");
        assertThat(imperial.getShelves()).extracting(Shelf::getOrder).containsExactly(1, 2, 3);
        assertThat(imperial.getShelves().get(0).getItemIds())
                .as("itemIds is display order and must be preserved exactly")
                .containsExactly("item_42", "item_env", "item_dual");

        // UCL bought nothing, so only open access appears and two shelves are hidden by being empty.
        FeedSettings ucl = feedSettings.findByInstitutionId("inst_ucl").orElseThrow();
        assertThat(ucl.getShelves().get(0).getItemIds()).containsExactly("item_ab6", "item_oa9");
        assertThat(ucl.getShelves().get(1).getItemIds()).isEmpty();
        assertThat(ucl.getShelves().get(2).getItemIds()).isEmpty();

        // 1:1 with institutions, including the suspended one.
        assertThat(feedSettings.count()).isEqualTo(institutions.count());
    }

    @Test
    @DisplayName("B's guards reject bad data, which is why the seed has to satisfy them")
    void personBsGuardsAreReallyActive() {
        // Not testing entity code for its own sake: these two guards are the reason the dataset must order
        // its inserts and carry exactly three shelves, and a future edit that breaks either would
        // otherwise fail at startup with a stack trace nobody expects.
        CatalogueItem orphan = items.findById("item_42").orElseThrow();
        orphan.setId("item_orphan");
        orphan.setPublisherId("pub_does_not_exist");
        assertThatThrownBy(() -> items.save(orphan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not reference an existing publisher");

        FeedSettings twoShelves = feedSettings.findByInstitutionId("inst_ucl").orElseThrow();
        twoShelves.setId("fs_broken");
        twoShelves.setInstitutionId("inst_broken");
        twoShelves.setShelves(List.of(twoShelves.getShelves().get(0), twoShelves.getShelves().get(1)));
        assertThatThrownBy(() -> feedSettings.save(twoShelves))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 3 shelves");
    }

    // ---------------------------------------------------------------------------------- helpers

    private DemoDataSeeder seederWithReset() {
        return new DemoDataSeeder(
                publishers,
                collections,
                institutions,
                items,
                entitlements,
                adminUsers,
                feedSettings,
                tools.jackson.databind.json.JsonMapper.builder().build(),
                mongoClient,
                mongoDatabaseFactory,
                MONGO.getReplicaSetUrl(),
                "localhost,127.0.0.1,::1,mongo",
                true);
    }

    private void assertCounts() {
        assertThat(publishers.count()).as("publishers").isEqualTo(2);
        assertThat(collections.count()).as("collections").isEqualTo(2);
        assertThat(institutions.count()).as("institutions").isEqualTo(3);
        assertThat(items.count()).as("catalogueItems").isEqualTo(8);
        assertThat(entitlements.count()).as("entitlements").isEqualTo(3);
        assertThat(adminUsers.count()).as("adminUsers").isEqualTo(3);
        assertThat(feedSettings.count()).as("feedSettings").isEqualTo(3);
    }

    private void assertUniqueIndexStillFires() {
        Publisher duplicatePublisher = publishers.findById("pub_rtlg").orElseThrow();
        duplicatePublisher.setId("pub_dupe");
        assertThatThrownBy(() -> publishers.save(duplicatePublisher))
                .as("unique index on publishers.code")
                .isInstanceOf(DuplicateKeyException.class);

        Institution duplicateInstitution = institutions.findById("inst_7f3").orElseThrow();
        duplicateInstitution.setId("inst_dupe");
        assertThatThrownBy(() -> institutions.save(duplicateInstitution))
                .as("unique index on institutions.code")
                .isInstanceOf(DuplicateKeyException.class);

        AdminUser duplicateAdmin = adminUsers.findById("adm_01").orElseThrow();
        duplicateAdmin.setId("adm_dupe");
        assertThatThrownBy(() -> adminUsers.save(duplicateAdmin))
                .as("unique index on adminUsers.email")
                .isInstanceOf(DuplicateKeyException.class);

        FeedSettings duplicateFeed = feedSettings.findByInstitutionId("inst_7f3").orElseThrow();
        duplicateFeed.setId("fs_dupe");
        assertThatThrownBy(() -> feedSettings.save(duplicateFeed))
                .as("unique index on feedSettings.institutionId")
                .isInstanceOf(DuplicateKeyException.class);
    }
}