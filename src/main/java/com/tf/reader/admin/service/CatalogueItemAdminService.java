package com.tf.reader.admin.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.CatalogueItemView;
import com.tf.reader.admin.dto.CatalogueItemWrite;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.WorkType;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.CatalogueItemSearchRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.ingest.service.CoverUrlResolver;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class CatalogueItemAdminService {

	private static final int DEFAULT_SIZE = 20;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 100;

	private final CatalogueItemRepository catalogueItemRepository;
	private final CatalogueItemSearchRepository searchRepository;
	private final PublisherRepository publisherRepository;
	private final BookCollectionRepository bookCollectionRepository;
	private final EntitlementRepository entitlementRepository;
	private final CatalogueVersionBumper catalogueVersionBumper;
	private final AdminAuditWriter auditWriter;
	private final AdminScopeAuthorizer adminScope;
	private final CoverUrlResolver coverUrlResolver;

	private static final List<EntitlementStatus> STATUS_RANK = List.of(EntitlementStatus.REVOKED,
			EntitlementStatus.SUSPENDED, EntitlementStatus.PENDING, EntitlementStatus.ACTIVE);

	public PageResponse<CatalogueItemView> list(String publisherIdScope, String publisherId, String collectionId,
			ContentType contentType, AccessTier accessTier, String q, Integer page, Integer size,
			String institutionId) {
		AdminRole role = adminScope.currentRole();
		if (role != AdminRole.SUPER_ADMIN && role != AdminRole.PUBLISHER_ADMIN && role != AdminRole.INSTITUTION_ADMIN) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE,
					"This operation requires SUPER_ADMIN, PUBLISHER_ADMIN or INSTITUTION_ADMIN.");
		}
		int resolvedPage = page == null ? 0 : page;
		if (resolvedPage < 0) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be zero or greater");
		}
		int resolvedSize = size == null ? DEFAULT_SIZE : size;
		if (resolvedSize < MIN_SIZE || resolvedSize > MAX_SIZE) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"size must be between " + MIN_SIZE + " and " + MAX_SIZE);
		}

		String resolvedPublisherId = publisherId;
		if (publisherIdScope != null) {
			if (publisherId != null && !publisherId.equals(publisherIdScope)) {
				throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to list another publisher's items");
			}
			resolvedPublisherId = publisherIdScope;
		}

		// An institution admin always sees their own institution's view, never one they pass in.
		// A publisher admin has no institution view at all: institutionId is SUPER_ADMIN only.
		String resolvedInstitutionId = switch (role) {
			case INSTITUTION_ADMIN -> adminScope.currentInstitutionScope();
			case SUPER_ADMIN -> institutionId;
			case PUBLISHER_ADMIN -> null;
		};

		CatalogueItemSearchRepository.Results results = searchRepository.search(resolvedPublisherId, collectionId,
				contentType, accessTier, blankToNull(q), resolvedPage, resolvedSize);

		Map<String, EntitlementStatus> statusByItemId = resolveEntitlementStatuses(resolvedInstitutionId,
				results.items());
		List<CatalogueItemView> items = results.items().stream()
				.map(item -> toSummaryView(item, resolvedInstitutionId != null, statusByItemId.get(item.getId())))
				.toList();
		return new PageResponse<>(items, resolvedPage, resolvedSize, results.total());
	}

	/**
	 * Loads the institution's entitlements once, not once per item, then matches each item by id,
	 * collectionIds and publisherId. An item can match more than one entitlement at different
	 * scopes; the strongest status wins, since the item is genuinely accessible if any covering
	 * entitlement grants it.
	 */
	private Map<String, EntitlementStatus> resolveEntitlementStatuses(String institutionId,
			List<CatalogueItem> items) {
		if (institutionId == null) {
			return Map.of();
		}
		List<Entitlement> entitlements = entitlementRepository.findByInstitutionId(institutionId, Pageable.unpaged())
				.getContent();

		Map<String, EntitlementStatus> result = new HashMap<>();
		for (CatalogueItem item : items) {
			EntitlementStatus best = null;
			for (Entitlement entitlement : entitlements) {
				boolean matches = switch (entitlement.getScopeType()) {
					case ITEM -> entitlement.getScopeId().equals(item.getId());
					case PUBLISHER -> entitlement.getScopeId().equals(item.getPublisherId());
					case COLLECTION -> item.getCollectionIds() != null
							&& item.getCollectionIds().contains(entitlement.getScopeId());
				};
				if (matches && (best == null || STATUS_RANK.indexOf(entitlement.getStatus()) > STATUS_RANK.indexOf(best))) {
					best = entitlement.getStatus();
				}
			}
			result.put(item.getId(), best);
		}
		return result;
	}


	public CatalogueItemView create(CatalogueItemWrite write) {
		if (!adminScope.canAccessPublisher(write.publisherId())) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to create a book for this publisher");
		}
		WorkType workType = write.workType() == null ? WorkType.BOOK : write.workType();
		requireValidParentChain(workType, write.parentId());
		requireLeafFieldsConsistentWithWorkType(workType, write);
		validateDuration(write);
		requireIsbnFree(write.isbn(), null);
		requireCollectionsBelongToPublisher(write.publisherId(), write.collectionIds());

		CatalogueItem item = new CatalogueItem();
		item.setId("item_" + UUID.randomUUID().toString().substring(0, 8));
		item.setWorkType(workType);
		applyWrite(item, write);
		item.setContentState(ContentState.NONE);
		item.setStatus(write.status() == null ? ItemStatus.DRAFT : write.status());
		item.setCreatedAt(Instant.now());
		item.setUpdatedAt(Instant.now());

		item = save(item);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.CREATE, "CATALOGUE_ITEM", item.getId(), null,
				afterMap(item));
		bumpIfPublishedOrArchived(item);

		return toFullView(item);
	}



	public CatalogueItemView get(String itemId) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);
		return toFullView(item);
	}



	public CatalogueItemView update(String itemId, CatalogueItemWrite write) {
		CatalogueItem item = findOrThrow(itemId);
		requireAccess(item);
		if (!adminScope.canAccessPublisher(write.publisherId())) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to move a book to this publisher");
		}
		WorkType workType = write.workType() == null ? WorkType.BOOK : write.workType();
		requireValidParentChain(workType, write.parentId());
		requireLeafFieldsConsistentWithWorkType(workType, write);
		validateDuration(write);
		requireIsbnImmutable(item.getIsbn(), write.isbn());
		requireIsbnFree(write.isbn(), itemId);
		requireCollectionsBelongToPublisher(write.publisherId(), write.collectionIds());

		Map<String, Object> before = afterMap(item);

		item.setWorkType(workType);
		applyWrite(item, write);
		if (write.status() != null) {
			item.setStatus(write.status());
		}
		item.setUpdatedAt(Instant.now());

		item = save(item);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.UPDATE, "CATALOGUE_ITEM", item.getId(), before,
				afterMap(item));
		bumpIfPublishedOrArchived(item);

		return toFullView(item);
	}



	private CatalogueItem findOrThrow(String itemId) {
		return catalogueItemRepository.findById(itemId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such catalogue item"));
	}

	private void requireAccess(CatalogueItem item) {
		if (!adminScope.canAccessPublisher(item.getPublisherId())) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to access this book");
		}
	}

	private void applyWrite(CatalogueItem item, CatalogueItemWrite write) {
		item.setPublisherId(write.publisherId());
		item.setCollectionIds(write.collectionIds());
		item.setParentId(write.parentId());
		item.setSequence(write.sequence());
		item.setTitle(write.title());
		item.setSubtitle(write.subtitle());
		item.setAuthors(write.authors());
		item.setEditors(write.editors());
		item.setNarrators(write.narrators());
		item.setIsbn(normalizeIsbn(write.isbn()));
		item.setContentType(write.contentType());
		item.setAccessTier(write.accessTier());
		item.setSubjects(write.subjects());
		item.setLanguage(write.language());
		item.setDescription(write.description());
		item.setPublishedAt(write.publishedAt());
		item.setDuration(write.duration());
		item.setCoverUrl(write.coverUrl());
	}

	private CatalogueItem save(CatalogueItem item) {
		try {
			return catalogueItemRepository.save(item);
		}
		catch (IllegalArgumentException ex) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, ex.getMessage());
		}
	}

	private void bumpIfPublishedOrArchived(CatalogueItem item) {
		if (item.getStatus() == ItemStatus.PUBLISHED || item.getStatus() == ItemStatus.ARCHIVED) {
			catalogueVersionBumper.bump(CatalogueVersionBumper.Scope.ITEM, item.getId());
		}
	}

	private void requireCollectionsBelongToPublisher(String publisherId, List<String> collectionIds) {
		if (collectionIds == null || collectionIds.isEmpty()) {
			return;
		}
		List<BookCollection> found = bookCollectionRepository.findAllById(collectionIds);
		Map<String, BookCollection> byId = new HashMap<>();
		for (BookCollection collection : found) {
			byId.put(collection.getId(), collection);
		}
		List<String> invalid = new ArrayList<>();
		for (String collectionId : collectionIds) {
			BookCollection collection = byId.get(collectionId);
			if (collection == null || !Objects.equals(collection.getPublisherId(), publisherId)) {
				invalid.add(collectionId);
			}
		}
		if (!invalid.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"Collections not belonging to publisher '" + publisherId + "': " + invalid);
		}
	}

	private static void validateDuration(CatalogueItemWrite write) {
		boolean audio = write.contentType() == ContentType.AUDIO;
		if (audio && write.duration() == null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "duration is required when contentType is AUDIO");
		}
		if (!audio && write.duration() != null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "duration is only meaningful when contentType is AUDIO");
		}
	}

	/**
	 * Only BOOK and ARTICLE are leaves: they carry a file and go through ingest, so contentType
	 * and accessTier are required exactly as they always were. JOURNAL/VOLUME/ISSUE are pure
	 * containers - the same fields would describe a file that will never exist, so they must be
	 * absent instead. isbn is allowed on either (an article is citable by ISBN same as a book) so
	 * it is not restricted here.
	 */
	private static void requireLeafFieldsConsistentWithWorkType(WorkType workType, CatalogueItemWrite write) {
		boolean leaf = workType == WorkType.BOOK || workType == WorkType.ARTICLE;
		if (leaf) {
			if (write.contentType() == null) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, "contentType is required for a " + workType);
			}
			if (write.accessTier() == null) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, "accessTier is required for a " + workType);
			}
		}
		else {
			if (write.contentType() != null || write.accessTier() != null || write.duration() != null) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED,
						"contentType, accessTier and duration are not meaningful for a " + workType + " container");
			}
		}
	}

	/**
	 * Enforces the one nesting shape this hierarchy allows: JOURNAL at the top, VOLUME under a
	 * JOURNAL, ISSUE under a VOLUME, ARTICLE under an ISSUE or standalone, BOOK always standalone.
	 * Checked here rather than assumed, because a wrong parent is a browsing dead end no OPDS
	 * client would ever detect on its own.
	 */
	private void requireValidParentChain(WorkType workType, String parentId) {
		WorkType requiredParentType = switch (workType) {
			case BOOK, JOURNAL -> null;
			case VOLUME -> WorkType.JOURNAL;
			case ISSUE -> WorkType.VOLUME;
			case ARTICLE -> null; // an ARTICLE's parent, if present, must be an ISSUE - checked below
		};

		if (requiredParentType == null && workType != WorkType.ARTICLE) {
			if (parentId != null) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, "a " + workType + " may not have a parentId");
			}
			return;
		}

		if (parentId == null) {
			if (workType == WorkType.ARTICLE) {
				return; // a standalone article
			}
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "a " + workType + " requires a parentId");
		}

		WorkType expected = workType == WorkType.ARTICLE ? WorkType.ISSUE : requiredParentType;
		CatalogueItem parent = catalogueItemRepository.findById(parentId)
				.orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "parentId does not exist: " + parentId));
		WorkType parentWorkType = parent.getWorkType() == null ? WorkType.BOOK : parent.getWorkType();
		if (parentWorkType != expected) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"a " + workType + "'s parent must be a " + expected + ", not a " + parentWorkType);
		}
	}

	/**
	 * A repeat ISBN is refused rather than linked to the book that already holds it. Putting an
	 * existing book into a collection is already
	 * available via the endpoint PUT /api/admin/v1/collections/{collectionId}/items ,and one URL that either creates
	 * or links depending on data the caller cannot see is worse than a 409.
	 *
	 * <p>Compares on the normalised form, so ISBN 978-0-13-235088-4 and ISBN 9780132350884
	 * are the same book. An absent ISBN is never a duplicate: it is optional , and most audiobooks and ingest-first drafts have none.
	 *
	 * @param editingItemId the item being updated, excluded from the match so a PUT that leaves
	 *                      the ISBN alone does not collide with itself; null when creating
	 */
	private void requireIsbnFree(String rawIsbn, String editingItemId) {
		String isbn = normalizeIsbn(rawIsbn);
		if (isbn == null || isbn.isBlank()) {
			return;
		}
		catalogueItemRepository.findByIsbn(isbn)
				.filter(existing -> !existing.getId().equals(editingItemId))
				.ifPresent(existing -> {
					throw new ApiException(ErrorCode.CODE_TAKEN, duplicateIsbnMessage(existing));
				});
	}

	// Naming the existing book tells an operator what to do next, but only when the book is
	// already theirs to see. To an admin scoped elsewhere it is another publisher's catalogue,
	// so they get the refusal without the id - same reason findActiveById hides a suspended
	// institution behind a 404.
	private String duplicateIsbnMessage(CatalogueItem existing) {
		if (!adminScope.canAccessPublisher(existing.getPublisherId())) {
			return "That ISBN is already in the catalogue";
		}
		return "ISBN already belongs to " + existing.getId()
				+ ". To add that book to a collection use PUT /api/admin/v1/collections/{collectionId}/items";
	}

	// Once an ISBN is set it is a fact about the physical book, not a form field an admin can
	// second-guess later - a later PUT that tries to change it is almost always a copy-paste
	// mistake, not an intentional correction, and an intentional one is a new book, not an edit.
	private static void requireIsbnImmutable(String existingIsbn, String newIsbn) {
		String existing = normalizeIsbn(existingIsbn);
		if (existing == null) {
			return;
		}
		if (!existing.equals(normalizeIsbn(newIsbn))) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "isbn cannot be changed once it has been set");
		}
	}

	private static String normalizeIsbn(String isbn) {
		return isbn == null ? null : isbn.replaceAll("[\\s-]", "").toUpperCase();
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}



	private CatalogueItemView toSummaryView(CatalogueItem item, boolean institutionView, EntitlementStatus status) {
		String entitlementStatusLabel = !institutionView ? null : (status == null ? "NONE" : status.name());
		return toView(item, null, List.of(), entitlementStatusLabel);
	}

	public CatalogueItemView toFullView(CatalogueItem item) {
		String publisherName = publisherRepository.findById(item.getPublisherId()).map(Publisher::getName)
				.orElse(null);
		List<CatalogueItemView.Asset> assets = item.getAssets() == null ? List.of()
				: item.getAssets().stream().map(this::toAssetView).toList();
		return toView(item, publisherName, assets, null);
	}

	private CatalogueItemView toView(CatalogueItem item, String publisherName, List<CatalogueItemView.Asset> assets,
			String entitlementStatusLabel) {
		return new CatalogueItemView(item.getId(), item.getPublisherId(), publisherName, item.getCollectionIds(),
				item.getWorkType(), item.getParentId(), item.getSequence(), item.getTitle(), item.getSubtitle(),
				item.getAuthors(), item.getEditors(), item.getNarrators(), item.getIsbn(), item.getContentType(),
				item.getAccessTier(), item.getSubjects(), item.getLanguage(), item.getDescription(),
				item.getPublishedAt(), item.getNumberOfPages(), item.getDuration(), coverUrlResolver.resolve(item),
				item.getStatus(), item.getContentState(), item.getContentError(), assets, item.getCreatedAt(),
				item.getUpdatedAt(), entitlementStatusLabel);
	}

	private CatalogueItemView.Asset toAssetView(CatalogueItem.Asset asset) {
		List<CatalogueItemView.Part> parts = asset.getParts() == null ? List.of()
				: asset.getParts().stream().map(this::toPartView).toList();
		return new CatalogueItemView.Asset(asset.getFormat(), asset.getMimeType(), asset.isEncrypted(), parts);
	}

	private CatalogueItemView.Part toPartView(CatalogueItem.Part part) {
		return new CatalogueItemView.Part(part.getPartNumber(), part.getTitle(), part.getSizeBytes(),
				part.getCipherLength(), part.isHasSearchIndex(), part.getIndexSkipReason(), part.getIndexTerms(),
				part.getContentState(), part.getContentError(), part.getUpdatedAt());
	}

	private static Map<String, Object> afterMap(CatalogueItem item) {
		Map<String, Object> m = new HashMap<>();
		m.put("publisherId", item.getPublisherId());
		m.put("title", item.getTitle());
		m.put("contentType", String.valueOf(item.getContentType()));
		m.put("accessTier", String.valueOf(item.getAccessTier()));
		m.put("status", String.valueOf(item.getStatus()));
		return m;
	}

}
