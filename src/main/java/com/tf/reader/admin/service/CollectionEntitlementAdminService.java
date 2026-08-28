package com.tf.reader.admin.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.CollectionEntitlementView;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.page.PageResponse;

import lombok.RequiredArgsConstructor;

/**
 * Every collection across every publisher, each tagged with the current institution's
 * entitlementStatus - the collection counterpart of {@link CatalogueItemAdminService#list}, one
 * scope level up. Built the same way: same pagination bounds, same scope resolution, same
 * lowercase status labels.
 */
@Service
@RequiredArgsConstructor
public class CollectionEntitlementAdminService {

	private static final int DEFAULT_SIZE = 20;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 100;

	private static final List<EntitlementStatus> STATUS_RANK = List.of(EntitlementStatus.REVOKED,
			EntitlementStatus.SUSPENDED, EntitlementStatus.PENDING, EntitlementStatus.ACTIVE);

	private final BookCollectionRepository bookCollectionRepository;
	private final EntitlementRepository entitlementRepository;
	private final AdminScopeAuthorizer adminScope;

	public PageResponse<CollectionEntitlementView> list(String publisherIdScope, String publisherId, Integer page,
			Integer size, String institutionId) {
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
				throw new ApiException(ErrorCode.FORBIDDEN_ROLE,
						"Not permitted to list another publisher's collections");
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

		Pageable pageable = PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.ASC, "name"));
		Page<BookCollection> results = resolvedPublisherId == null ? bookCollectionRepository.findAll(pageable)
				: bookCollectionRepository.findByPublisherId(resolvedPublisherId, pageable);

		Map<String, EntitlementStatus> statusByCollectionId = resolveEntitlementStatuses(resolvedInstitutionId,
				results.getContent());
		List<CollectionEntitlementView> items = results.getContent().stream()
				.map(collection -> toView(collection, resolvedInstitutionId != null,
						statusByCollectionId.get(collection.getId())))
				.toList();
		return new PageResponse<>(items, resolvedPage, resolvedSize, results.getTotalElements());
	}

	/**
	 * A collection's status is never aggregated from its books - only COLLECTION and PUBLISHER
	 * scoped entitlements count, ITEM is dropped entirely, since an item-level grant says nothing
	 * about the collection as a package. Loads the institution's entitlements once, not once per
	 * collection; the strongest matching status wins, same ranking as
	 * {@link CatalogueItemAdminService}.
	 */
	private Map<String, EntitlementStatus> resolveEntitlementStatuses(String institutionId,
			List<BookCollection> collections) {
		if (institutionId == null) {
			return Map.of();
		}
		List<Entitlement> entitlements = entitlementRepository.findByInstitutionId(institutionId, Pageable.unpaged())
				.getContent();

		Map<String, EntitlementStatus> result = new HashMap<>();
		for (BookCollection collection : collections) {
			EntitlementStatus best = null;
			for (Entitlement entitlement : entitlements) {
				boolean matches = switch (entitlement.getScopeType()) {
					case COLLECTION -> entitlement.getScopeId().equals(collection.getId());
					case PUBLISHER -> entitlement.getScopeId().equals(collection.getPublisherId());
					case ITEM -> false;
				};
				if (matches
						&& (best == null || STATUS_RANK.indexOf(entitlement.getStatus()) > STATUS_RANK.indexOf(best))) {
					best = entitlement.getStatus();
				}
			}
			result.put(collection.getId(), best);
		}
		return result;
	}

	private CollectionEntitlementView toView(BookCollection collection, boolean institutionView,
			EntitlementStatus status) {
		String entitlementStatusLabel = !institutionView ? null : (status == null ? "none" : status.name().toLowerCase());
		return new CollectionEntitlementView(collection.getId(), collection.getPublisherId(), collection.getCode(),
				collection.getName(), collection.getDescription(), entitlementStatusLabel);
	}

}
