package com.tf.reader.catalogue.opds.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.CatalogueItem.Asset;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.opds.dto.Copies;
import com.tf.reader.catalogue.opds.dto.EncryptedInfo;
import com.tf.reader.catalogue.opds.dto.OpdsAvailability;
import com.tf.reader.catalogue.opds.dto.IndirectAcquisition;
import com.tf.reader.catalogue.opds.dto.OpdsContributor;
import com.tf.reader.catalogue.opds.dto.OpdsImageLink;
import com.tf.reader.catalogue.opds.dto.OpdsLink;
import com.tf.reader.catalogue.opds.dto.OpdsLinkProperties;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationMetadata;
import com.tf.reader.catalogue.service.CatalogueUrlBuilder;
import com.tf.reader.catalogue.service.FlambeauUrlBuilder;

import lombok.RequiredArgsConstructor;

/**
 * Turns one entitled {@link CatalogueItem} into the {@code OpdsPublication} shape
 * {@code wokay-api.yaml} defines. Reused by the root feed and the group feed; the
 * publication-detail endpoint (out of scope this week) will reuse it too.
 */
@Component
@RequiredArgsConstructor
class OpdsPublicationMapper {

    private static final String BOOK_TYPE = "http://schema.org/Book";
    private static final String AUDIOBOOK_TYPE = "http://schema.org/Audiobook";

    // The acquisition href returns flambeau JSON, never the book itself - the real media
    // type lives in properties.indirectAcquisition[0].type instead (wokay-api.yaml).
    private static final String ACQUISITION_LINK_TYPE = "application/json";
    private static final String PUBLICATION_LINK_TYPE = "application/opds-publication+json";
    private static final String ENCRYPTION_ALGORITHM = "http://www.w3.org/2009/xmlenc11#aes256-gcm";
    private static final String SUBSCRIBE_LINK_TITLE = "Available through your institution";

    // Open access needs no grant lookup at all (EntitlementQueryImpl), so a fixed "entitled"
    // decision is exactly as correct here as a real one - there is nothing a real check could add.
    private static final EntitlementDecision OPEN_ACCESS_DECISION =
            new EntitlementDecision(true, AccessLevel.OPEN_ACCESS, null, null, 0, null, null);

    private final CatalogueUrlBuilder catalogueUrlBuilder;
    private final FlambeauUrlBuilder flambeauUrlBuilder;

    OpdsPublication toPublication(CatalogueItem item, EntitlementDecision decision, String institutionId,
            Map<String, Publisher> publishersById) {
        return toPublicationWithSelfHref(item, decision,
                catalogueUrlBuilder.publicationUrlFor(institutionId, item.getId()), publishersById);
    }


    OpdsPublication toPublicationWithSelfHref(CatalogueItem item, EntitlementDecision decision, String selfHref,
            Map<String, Publisher> publishersById) {
        List<OpdsLink> links = List.of(
                new OpdsLink("self", selfHref, PUBLICATION_LINK_TYPE),
                acquisitionLink(item, decision));
        return new OpdsPublication(metadata(item, publishersById), links, coverImages(item));
    }

    // Discovery search (Workstream 9) has no institution and runs no entitlement check at all -
    // an open access book gets the real acquisition link, anything else gets a subscribe link,
    // regardless of what any caller happens to hold, since there is no caller to check against.
    OpdsPublication toDiscoveryPublication(CatalogueItem item, String selfHref, Map<String, Publisher> publishersById) {
        OpdsLink link = item.getAccessTier() == AccessTier.OPEN_ACCESS
                ? acquisitionLink(item, OPEN_ACCESS_DECISION)
                : subscribeLink(item);
        List<OpdsLink> links = List.of(new OpdsLink("self", selfHref, PUBLICATION_LINK_TYPE), link);
        return new OpdsPublication(metadata(item, publishersById), links, coverImages(item));
    }

    private OpdsLink subscribeLink(CatalogueItem item) {
        OpdsLinkProperties properties = new OpdsLinkProperties(item.getAccessTier(), OpdsAvailability.UNAVAILABLE);
        return new OpdsLink("http://opds-spec.org/acquisition/subscribe", catalogueUrlBuilder.institutionsUrl(),
                ACQUISITION_LINK_TYPE, SUBSCRIBE_LINK_TITLE, null, properties);
    }

    private OpdsPublicationMetadata metadata(CatalogueItem item, Map<String, Publisher> publishersById) {
        boolean audio = item.getContentType() == ContentType.AUDIO;
        Publisher publisher = publishersById.get(item.getPublisherId());
        return new OpdsPublicationMetadata(
                audio ? AUDIOBOOK_TYPE : BOOK_TYPE,
                identifierFor(item),
                item.getTitle(),
                item.getSubtitle(),
                contributors(item.getAuthors()),
                contributors(item.getEditors()),
                contributors(item.getNarrators()),
                publisher == null ? null : new OpdsContributor(publisher.getName(), null),
                item.getLanguage(),
                item.getPublishedAt(),
                item.getUpdatedAt(),
                item.getDescription(),
                audio ? null : item.getNumberOfPages(),
                audio ? item.getDuration() : null,
                contributors(item.getSubjects()));
    }

    private String identifierFor(CatalogueItem item) {
        String isbn = item.getIsbn();
        return isbn == null || isbn.isBlank() ? null : "urn:isbn:" + isbn;
    }

    private List<OpdsContributor> contributors(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        return names.stream().map(name -> new OpdsContributor(name, null)).toList();
    }

    private List<OpdsImageLink> coverImages(CatalogueItem item) {
        String coverUrl = item.getCoverUrl();
        if (coverUrl == null || coverUrl.isBlank()) {
            return null;
        }
        return List.of(new OpdsImageLink(coverUrl, imageMimeType(coverUrl), null, null));
    }

    // Covers are URLs an operator pastes in, pointing at a bucket we never read - the
    // extension is all we have, and an unrecognised one is omitted rather than guessed.
    private String imageMimeType(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return null;
    }

    private OpdsLink acquisitionLink(CatalogueItem item, EntitlementDecision decision) {
        AccessTier tier = item.getAccessTier();
        String rel = switch (tier) {
            case OPEN_ACCESS -> "http://opds-spec.org/acquisition/open-access";
            case SUBSCRIPTION -> "http://opds-spec.org/acquisition";
            case ELITE -> "http://opds-spec.org/acquisition/borrow";
        };
        String href = tier == AccessTier.OPEN_ACCESS
                ? flambeauUrlBuilder.readingSessionsUrlFor(item.getId())
                : flambeauUrlBuilder.loansUrlFor(item.getId());
        return new OpdsLink(rel, href, ACQUISITION_LINK_TYPE, null, null, propertiesFor(item, tier, decision));
    }

    private OpdsLinkProperties propertiesFor(CatalogueItem item, AccessTier tier, EntitlementDecision decision) {
        Asset asset = matchingAsset(item);
        Copies copies = tier == AccessTier.ELITE && decision.copies() != null
                ? new Copies(decision.copies())
                : null;
        EncryptedInfo encrypted = asset != null && asset.isEncrypted()
                ? new EncryptedInfo(ENCRYPTION_ALGORITHM, asset.getSizeBytes())
                : null;
        List<IndirectAcquisition> indirect = asset == null
                ? null
                : List.of(new IndirectAcquisition(asset.getMimeType()));
        boolean hasSearchIndex = asset != null && asset.isHasSearchIndex();
        boolean canPersist = tier != AccessTier.ELITE;
        // What actually crosses the wire: cipherLength for an encrypted asset (sizeBytes is the
        // plaintext length, already used for EncryptedInfo.originalLength above), sizeBytes
        // otherwise - same distinction content/api/SignedUrl draws between the two fields.
        Long fileSize = asset == null ? null : (asset.isEncrypted() ? asset.getCipherLength() : asset.getSizeBytes());
        return new OpdsLinkProperties(tier, indirect, copies, encrypted, hasSearchIndex, canPersist, fileSize, null);
    }

    private Asset matchingAsset(CatalogueItem item) {
        if (item.getAssets() == null) {
            return null;
        }
        return item.getAssets().stream()
                .filter(asset -> asset.getFormat() == item.getContentType())
                .findFirst()
                .orElse(null);
    }
}
