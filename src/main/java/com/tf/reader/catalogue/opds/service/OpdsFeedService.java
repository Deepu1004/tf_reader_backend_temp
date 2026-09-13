package com.tf.reader.catalogue.opds.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.FeedSettings;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Shelf;
import com.tf.reader.catalogue.entity.WorkType;
import com.tf.reader.catalogue.opds.dto.OpdsFeedMetadata;
import com.tf.reader.catalogue.opds.dto.OpdsGroup;
import com.tf.reader.catalogue.opds.dto.OpdsGroupMetadata;
import com.tf.reader.catalogue.opds.dto.OpdsLink;
import com.tf.reader.catalogue.opds.dto.OpdsNavigationFeed;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationDocument;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.FeedSettingsRepository;
import com.tf.reader.catalogue.repository.InstitutionSearchRepository;
import com.tf.reader.catalogue.service.CatalogueUrlBuilder;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageQuery;

import lombok.RequiredArgsConstructor;

/**
 * Builds the two OPDS institution feeds (Workstream 5): the root/navigation feed, and one
 * curated shelf. Whole-catalogue paging lives in {@link OpdsCatalogueQuery}; per-item
 * entitlement filtering and mapping lives in {@link OpdsEntitlementFilter}.
 */
@Service
@RequiredArgsConstructor
public class OpdsFeedService {

    private static final List<String> SHELF_GROUP_IDS = List.of("shelf_1", "shelf_2", "shelf_3");
    private static final int GROUP_PREVIEW_SIZE = 10;
    private static final String OPDS_MEDIA_TYPE = "application/opds+json";

    private final InstitutionSearchRepository institutionSearchRepository;
    private final FeedSettingsRepository feedSettingsRepository;
    private final CatalogueItemRepository catalogueItemRepository;
    private final OpdsEntitlementFilter entitlementFilter;
    private final OpdsCatalogueQuery catalogueQuery;
    private final OpdsSearchQuery searchQuery;
    private final CatalogueUrlBuilder catalogueUrlBuilder;

    /**
     * ACTIVE only, and NOT_FOUND rather than a 403 when it is not - a suspended institution has to
     * read exactly like an unknown one, or its existence is disclosed to anyone who can type an id
     * into a feed URL. Same rule and same reason as
     * {@link InstitutionSearchRepository#findActiveById} and {@code InstitutionLookupImpl}; the
     * status filter lives in the query rather than in a check after the read so there is no path
     * that forgets it.
     *
     * <p>Every OPDS institution endpoint goes through here, so this is the single gate for all
     * four.
     */
    public Institution loadInstitution(String institutionId) {
        return institutionSearchRepository.findActiveById(institutionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such institution"));
    }

    public OpdsNavigationFeed rootFeed(Institution institution, SubjectRef subject) {
        String institutionId = institution.getId();
        Optional<FeedSettings> settings = feedSettingsRepository.findByInstitutionId(institutionId);
        List<Shelf> shelves = settings.map(FeedSettings::getShelves).orElse(List.of());

        List<OpdsGroup> groups = shelves.stream()
        .sorted(Comparator.comparingInt(Shelf::getOrder))
        .limit(3)
        .map(shelf -> buildGroup(shelf, institutionId, subject))
        .filter(Objects::nonNull)
        .toList();

        List<OpdsLink> links = List.of(
                new OpdsLink("self", catalogueUrlBuilder.catalogueUrlFor(institutionId), OPDS_MEDIA_TYPE),
                searchLink(institutionId));

        List<OpdsLink> navigation = new ArrayList<>();
        navigation.add(new OpdsLink("subsection",
                catalogueUrlBuilder.groupUrlFor(institutionId, OpdsCatalogueQuery.ALL_GROUP_ID), OPDS_MEDIA_TYPE,
                "All titles"));
        // Additive signpost: a top-level Journal alongside the curated shelves. No schema
        // change - one more OpdsLink in the same navigation array a client already iterates.
        catalogueItemRepository.findByWorkTypeAndStatus(WorkType.JOURNAL, ItemStatus.PUBLISHED).stream()
                .sorted(Comparator.comparing(CatalogueItem::getSequence, Comparator.nullsLast(Integer::compareTo)))
                .forEach(journal -> navigation.add(new OpdsLink("subsection",
                        catalogueUrlBuilder.workUrlFor(institutionId, journal.getId()), OPDS_MEDIA_TYPE,
                        journal.getTitle())));

        String title = settings.map(FeedSettings::getFeedTitle).filter(t -> !t.isBlank())
                .orElse(institution.getName());
        int numberOfItems = entitlementFilter.countEntitled(
                catalogueItemRepository.findByStatusAndContentState(ItemStatus.PUBLISHED, ContentState.READY,
                        Sort.unsorted()),
                subject);
        OpdsFeedMetadata metadata = new OpdsFeedMetadata(title, numberOfItems, null, null, institution.getUpdatedAt());
        return new OpdsNavigationFeed(metadata, links, navigation, groups.isEmpty() ? null : groups);
    }

    private OpdsGroup buildGroup(Shelf shelf, String institutionId, SubjectRef subject) {
        List<OpdsPublication> entitled = entitlementFilter.mapEntitled(itemsForShelf(shelf), institutionId, subject);
        if (entitled.isEmpty()) {
            return null;
        }
        // numberOfItems is the shelf's real entitled count, not how many are embedded here -
        // the preview is a sample of the shelf, not the whole of it.
        OpdsGroupMetadata metadata = new OpdsGroupMetadata(shelf.getTitle(), entitled.size());
        OpdsLink self = new OpdsLink("self", catalogueUrlBuilder.groupUrlFor(institutionId, shelf.getId()),
                OPDS_MEDIA_TYPE, "Open this shelf");
        List<OpdsPublication> preview = entitled.stream().limit(GROUP_PREVIEW_SIZE).toList();
        return new OpdsGroup(metadata, List.of(self), preview);
    }

    public OpdsPublicationFeed groupFeed(Institution institution, String groupId, SubjectRef subject,
            PageQuery page, String sortParam, ContentType contentTypeFilter, AccessTier accessTierFilter) {
        if (SHELF_GROUP_IDS.contains(groupId)) {
            return curatedShelfFeed(institution, groupId, subject);
        }
        if (OpdsCatalogueQuery.ALL_GROUP_ID.equals(groupId)) {
            return catalogueQuery.allFeed(institution, subject, page, sortParam, contentTypeFilter, accessTierFilter);
        }
        throw new ApiException(ErrorCode.NOT_FOUND, "No such group");
    }

    private OpdsPublicationFeed curatedShelfFeed(Institution institution, String groupId, SubjectRef subject) {
        String institutionId = institution.getId();
        Shelf shelf = feedSettingsRepository.findByInstitutionId(institutionId)
                .map(FeedSettings::getShelves).orElse(List.of()).stream()
                .filter(s -> groupId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such group"));

        List<OpdsPublication> publications = entitlementFilter
                .mapEntitled(itemsForShelf(shelf), institutionId, subject);
        if (publications.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "No such group");
        }
        OpdsFeedMetadata metadata = new OpdsFeedMetadata(shelf.getTitle(), publications.size(), null, null,
                institution.getUpdatedAt());
        OpdsLink self = new OpdsLink("self", catalogueUrlBuilder.groupUrlFor(institutionId, groupId),
                OPDS_MEDIA_TYPE);
        return new OpdsPublicationFeed(metadata, List.of(self, searchLink(institutionId)), publications, null);
    }

    // Exact stored order, never re-sorted - the shelf's own curation is the order. Archived/
    // draft/not-yet-ready items are dropped here explicitly rather than left to
    // EntitlementQuery's own PUBLISHED+READY check, so a stale shelf entry never even reaches
    // an entitlement lookup for an item that could not be shown regardless of the answer.
    private List<CatalogueItem> itemsForShelf(Shelf shelf) {
        List<String> itemIds = shelf.getItemIds() == null ? List.of() : shelf.getItemIds();
        Map<String, CatalogueItem> byId = catalogueItemRepository.findAllById(itemIds).stream()
                .filter(item -> item.getStatus() == ItemStatus.PUBLISHED && item.getContentState() == ContentState.READY)
                .collect(Collectors.toMap(CatalogueItem::getId, item -> item));
        return itemIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public OpdsPublicationFeed searchFeed(Institution institution, SubjectRef subject, String queryText,
            PageQuery page, ContentType contentTypeFilter, AccessTier accessTierFilter) {
        return searchQuery.search(institution, subject, queryText, page, contentTypeFilter, accessTierFilter);
    }

    // Unknown item, archived/unpublished item and an unentitled item all reach this same
    // NOT_FOUND - the three are deliberately indistinguishable per wokay-api.yaml, so the
    // catalogue cannot be mapped by walking identifiers.
    public OpdsPublicationDocument publicationDocument(Institution institution, String itemId, SubjectRef subject) {
        CatalogueItem item = catalogueItemRepository.findById(itemId)
                .filter(candidate -> candidate.getStatus() == ItemStatus.PUBLISHED
                        && candidate.getContentState() == ContentState.READY)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publication"));
        OpdsPublication publication = entitlementFilter.mapIfEntitled(item, institution.getId(), subject)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publication"));
        return new OpdsPublicationDocument(publication.metadata(), publication.links(), publication.images());
    }

    /**
     * A JOURNAL or VOLUME returns navigation (one link per child, pointing at that child's own
     * {@code works/{id}}); an ISSUE returns a publication feed of its ARTICLE children, the same
     * shape a shelf already returns. Additive, {@code DRAFT}: nothing here touches the FROZEN
     * {@code groups/{groupId}} contract. 404 for anything not a JOURNAL/VOLUME/ISSUE, or not
     * PUBLISHED - same indistinguishable-404 rule the rest of OPDS already uses.
     */
    public Object workFeed(Institution institution, String workId, SubjectRef subject) {
        CatalogueItem work = catalogueItemRepository.findById(workId)
                .filter(item -> item.getStatus() == ItemStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such work"));
        WorkType workType = work.getWorkType();
        String institutionId = institution.getId();

        if (workType == WorkType.JOURNAL || workType == WorkType.VOLUME) {
            List<CatalogueItem> children = catalogueItemRepository.findByParentId(workId).stream()
                    .filter(child -> child.getStatus() == ItemStatus.PUBLISHED)
                    .sorted(Comparator.comparing(CatalogueItem::getSequence, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            List<OpdsLink> navigation = children.stream()
                    .map(child -> new OpdsLink("subsection", catalogueUrlBuilder.workUrlFor(institutionId, child.getId()),
                            OPDS_MEDIA_TYPE, child.getTitle()))
                    .toList();
            OpdsLink self = new OpdsLink("self", catalogueUrlBuilder.workUrlFor(institutionId, workId), OPDS_MEDIA_TYPE);
            OpdsFeedMetadata metadata = new OpdsFeedMetadata(work.getTitle(), navigation.size(), null, null,
                    work.getUpdatedAt());
            return new OpdsNavigationFeed(metadata, List.of(self), navigation, null);
        }

        if (workType == WorkType.ISSUE) {
            List<CatalogueItem> articles = catalogueItemRepository.findByParentId(workId).stream()
                    .filter(child -> child.getStatus() == ItemStatus.PUBLISHED
                            && child.getContentState() == ContentState.READY)
                    .sorted(Comparator.comparing(CatalogueItem::getSequence, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
            List<OpdsPublication> publications = entitlementFilter.mapEntitled(articles, institutionId, subject);
            OpdsLink self = new OpdsLink("self", catalogueUrlBuilder.workUrlFor(institutionId, workId), OPDS_MEDIA_TYPE);
            OpdsFeedMetadata metadata = new OpdsFeedMetadata(work.getTitle(), publications.size(), null, null,
                    work.getUpdatedAt());
            if (publications.isEmpty()) {
                // The OPDS schema forbids an empty publications array - a link back, same rule a
                // curated shelf already follows.
                List<OpdsLink> navigation = List.of(new OpdsLink("up",
                        catalogueUrlBuilder.catalogueUrlFor(institutionId), OPDS_MEDIA_TYPE));
                return new OpdsPublicationFeed(metadata, List.of(self), null, navigation);
            }
            return new OpdsPublicationFeed(metadata, List.of(self), publications, null);
        }

        throw new ApiException(ErrorCode.NOT_FOUND, "No such work");
    }

    private OpdsLink searchLink(String institutionId) {
        return new OpdsLink("search", catalogueUrlBuilder.searchUrlTemplateFor(institutionId), OPDS_MEDIA_TYPE,
                "Search this catalogue", true, null);
    }
}

