package com.tf.reader.admin.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.CollectionItemsResult;
import com.tf.reader.admin.dto.CollectionItemsWrite;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class CollectionAdminService {

	private final BookCollectionRepository bookCollectionRepository;
	private final CatalogueItemRepository catalogueItemRepository;
	private final CatalogueVersionBumper catalogueVersionBumper;
	private final AdminAuditWriter auditWriter;
	private final AdminScopeAuthorizer adminScope;

	public CollectionItemsResult setItems(String collectionId, CollectionItemsWrite write) {
		BookCollection collection = bookCollectionRepository.findById(collectionId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such collection"));

		if (!adminScope.canAccessPublisher(collection.getPublisherId())) {
			throw new ApiException(ErrorCode.FORBIDDEN_SCOPE, "Not permitted to access this collection");
		}

		Set<String> requestedIds = new LinkedHashSet<>(write.itemIds());

		List<CatalogueItem> requestedItems = catalogueItemRepository.findAllById(requestedIds);
		if (requestedItems.size() != requestedIds.size()) {
			Set<String> foundIds = requestedItems.stream().map(CatalogueItem::getId)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			Set<String> missing = new LinkedHashSet<>(requestedIds);
			missing.removeAll(foundIds);
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown catalogue item ids: " + missing);
		}

		List<CatalogueItem> currentMembers = catalogueItemRepository.findByCollectionIds(collectionId);
		List<String> beforeIds = currentMembers.stream().map(CatalogueItem::getId).toList();

		for (CatalogueItem item : requestedItems) {
			List<String> collectionIds = item.getCollectionIds() == null ? List.of() : item.getCollectionIds();
			if (!collectionIds.contains(collectionId)) {
				List<String> updated = new ArrayList<>(collectionIds);
				updated.add(collectionId);
				item.setCollectionIds(updated);
				catalogueItemRepository.save(item);
			}
		}

		for (CatalogueItem item : currentMembers) {
			if (!requestedIds.contains(item.getId())) {
				List<String> updated = new ArrayList<>(item.getCollectionIds());
				updated.remove(collectionId);
				item.setCollectionIds(updated);
				catalogueItemRepository.save(item);
			}
		}

		auditWriter.record(AuditLog.Action.UPDATE, "COLLECTION", collectionId, Map.of("itemIds", beforeIds),
				Map.of("itemIds", List.copyOf(requestedIds)));

		List<String> affectedInstitutions = catalogueVersionBumper.bump(CatalogueVersionBumper.Scope.COLLECTION,
				collectionId);

		return new CollectionItemsResult(collectionId, requestedIds.size(), affectedInstitutions);
	}

}
