package com.tf.reader.catalogue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.dto.BatchItem;
import com.tf.reader.catalogue.dto.BatchItemsRequest;
import com.tf.reader.catalogue.dto.BatchItemsResponse;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.ingest.service.CoverUrlResolver;

import lombok.RequiredArgsConstructor;

/**
 * Turns a list of item ids into details, for a caller who only holds ids - a loan history, a
 * shelf. One lookup, one entitlement check per id, three buckets: what the caller may see, what
 * does not exist (including archived), and what exists but is not theirs.
 */
@Service
@RequiredArgsConstructor
public class CatalogueBatchService {

	/** The contract caps a call at 100 ids; more is 400 TOO_MANY_IDS, checked before any query. */
	private static final int MAX_IDS = 100;

	private final CatalogueItemRepository catalogueItemRepository;
	private final EntitlementQuery entitlementQuery;
	private final CoverUrlResolver coverUrlResolver;

	public BatchItemsResponse batch(SubjectRef subject, BatchItemsRequest request) {
		List<String> ids = request.ids();
		if (ids.size() > MAX_IDS) {
			throw new ApiException(ErrorCode.TOO_MANY_IDS,
					"At most " + MAX_IDS + " ids per call; got " + ids.size());
		}

		Map<String, CatalogueItem> found = catalogueItemRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(CatalogueItem::getId, Function.identity()));

		List<BatchItem> items = new ArrayList<>();
		List<String> notFound = new ArrayList<>();
		List<String> denied = new ArrayList<>();

		for (String id : ids) {
			CatalogueItem item = found.get(id);
			// An archived (or still-draft) item is treated the same as one that does not exist -
			// the contract is explicit that notFound covers both.
			if (item == null || item.getStatus() != ItemStatus.PUBLISHED) {
				notFound.add(id);
				continue;
			}

			EntitlementDecision decision = entitlementQuery.check(subject, id);
			if (!decision.entitled()) {
				denied.add(id);
				continue;
			}

			items.add(toBatchItem(item, decision));
		}

		return new BatchItemsResponse(items, notFound, denied);
	}

	private BatchItem toBatchItem(CatalogueItem item, EntitlementDecision decision) {
		boolean hasSearchIndex = item.getAssets() != null
				&& item.getAssets().stream().anyMatch(CatalogueItem.Asset::isHasSearchIndex);

		return new BatchItem(item.getId(), item.getTitle(), item.getAuthors(), coverUrlResolver.resolve(item),
				item.getIsbn(), item.getContentType(), item.getAccessTier(), decision.copies(), hasSearchIndex);
	}

}
