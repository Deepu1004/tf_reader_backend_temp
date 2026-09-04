package com.tf.reader.admin.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * All seven collections now map to a real entity 
 * few fields differ slightly (nesting, field count, types) — see the seeder for specifics.
 * AuditLog isn't seeded deliberately, so it is not represented here. The seed does not write to it
 *
 * One deliberate mismatch: {@code SeedAsset.cipherLength}/{@code indexTerms} stay
 * nullable here even though fields are primitives, since "not encrypted" and "zero
 * bytes" are different facts. {@link DemoDataSeeder} maps null to 0 on the way in.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SeedDataset(
        List<SeedPublisher> publishers,
        List<SeedCollection> collections,
        List<SeedInstitution> institutions,
        List<SeedItem> catalogueItems,
        List<SeedEntitlement> entitlements,
        List<SeedAdminUser> adminUsers,
        List<SeedFeedSettings> feedSettings) {

    /** Total documents the seed writes. Asserted in the tests so a silent addition cannot happen. */
    public int documentCount() {
        return publishers.size()
                + collections.size()
                + institutions.size()
                + catalogueItems.size()
                + entitlements.size()
                + adminUsers.size()
                + feedSettings.size();
    }

    /**
     * Maps to catalogue.entity.Publisher
     *
     * <p>{@code id, code, name, description, logoUrl, status, createdAt, updatedAt}. This is the one
     * class where @AllArgsConstructor} was nor used they hand-wrote the eight-argument
     * constructor so  code passes through a normalize() that upper-cases it. Same
     * signature, same result for this dataset, and the uppercase rule the handbook only stated is now
     * enforced by the entity.
     * /@Indexed(unique = true)}, so the two seeded codes must differ, and
     * they do
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedPublisher(
            @JsonProperty("_id") String id,
            String code,
            String name,
            String description,
            String logoUrl,
            String status,
            Instant createdAt,
            Instant updatedAt) {}

    // Above are the seven top-level collections. Below are the embedded types that appear in one or more of them. All are immutable records, so they have no setters and no no-arg constructor, which is why Jackson needs @JsonProperty("_id") on every id field.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedCollection(
            @JsonProperty("_id") String id,
            String publisherId,
            String code,
            String name,
            String description) {}

    /// The seven top-level collections are the only ones that get a record of their own. All other types are embedded in one or more of them, and they are declared here as records so Jackson can map them. All are immutable, so they have no setters and no no-arg constructor, which is why Jackson needs @JsonProperty("_id") on every id field.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedInstitution(
            @JsonProperty("_id") String id,
            String code,
            String name,
            String type,
            String country,
            String city,
            SeedBranding branding,
            SeedSignIn signIn,
            String status,
            long catalogueVersion,
            Instant createdAt,
            Instant updatedAt) {}

    /** Maps to catalogue.entity.Branding, top-level and embedded. Identical. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedBranding(String logoUrl, String primaryColor) {}

    //maps to catalogue.entity.SignIn, top-level and embedded. Identical.the entity has two fields, so the seed carries both.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedSignIn(String method, String idpHint) {}

   //below SeedItem, SeedAsset, SeedEntitlement, SeedAdminUser, SeedFeedSettings, and SeedShelf are all embedded types that appear in one or more of the top-level collections. All are immutable records, so they have no setters and no no-arg constructor, which is why Jackson needs @JsonProperty("_id") on every id field.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedItem(
            @JsonProperty("_id") String id,
            String publisherId,
            List<String> collectionIds,
            String title,
            String subtitle,
            List<String> authors,
            List<String> editors,
            List<String> narrators,
            String isbn,
            String language,
            String description,
            List<String> subjects,
            LocalDate publishedAt,
            String coverUrl,
            String coverKey,
            String coverMimeType,
            String contentType,
            String accessTier,
            String status,
            String contentState,
            String contentError,
            List<SeedAsset> assets,
            String storageKey,
            String indexKey,
            String wrappedBek,
            Instant createdAt,
            Instant updatedAt) {

        /** A book reaches a feed only when both halves agree. Handbook section 06. */
        public boolean isFeedVisible() {
            return "PUBLISHED".equals(status) && "READY".equals(contentState);
        }
    }

    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedAsset(
            String format,
            String mimeType,
            long sizeBytes,
            Long cipherLength,
            boolean encrypted,
            boolean hasSearchIndex,
            Integer indexTerms,
            String indexSkipReason,
            String keyId) {}

    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedEntitlement(
            @JsonProperty("_id") String id,
            String institutionId,
            String scopeType,
            String scopeId,
            Integer copies,
            int loanPeriodDays,
            LocalDate validFrom,
            LocalDate validTo,
            String status,
            Instant createdAt,
            Instant updatedAt) {}

    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedAdminUser(
            @JsonProperty("_id") String id,
            String email,
            String name,
            String passwordHash,
            String role,
            String publisherId,
            String institutionId,
            String status,
            Instant lastLoginAt) {}

   
    @JsonIgnoreProperties(ignoreUnknown = true)// Maps to catalogue.entity.FeedSettings, top-level and embedded. Identical.
    public record SeedFeedSettings(
            @JsonProperty("_id") String id,
            String institutionId,
            String feedTitle,
            int pageSize,
            String defaultSort,
            List<SeedShelf> shelves,
            Instant updatedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)// Maps to catalogue.entity.Shelf, embedded in FeedSettings. Identical.
    public record SeedShelf(String id, String title, int order, List<String> itemIds) {}
}