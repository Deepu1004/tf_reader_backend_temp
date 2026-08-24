package com.tf.reader.catalogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.dto.BatchItem;
import com.tf.reader.catalogue.dto.BatchItemsRequest;
import com.tf.reader.catalogue.dto.BatchItemsResponse;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/** Business rules for turning a list of ids into details, tested without a servlet or a database. */
class CatalogueBatchServiceTest {

	private final CatalogueItemRepository catalogueItemRepository = mock(CatalogueItemRepository.class);
	private final EntitlementQuery entitlementQuery = mock(EntitlementQuery.class);

	private final CatalogueBatchService service = new CatalogueBatchService(catalogueItemRepository, entitlementQuery);

	private static final SubjectRef SUBJECT = new SubjectRef("usr_1", "inst_7f3");

	@Test
	void splitsAllowedDeniedNotFoundAndArchivedAcrossThreeLists() {
		CatalogueItem allowed = item("item_allowed", "Rights for Robots", ItemStatus.PUBLISHED);
		CatalogueItem deniedItem = item("item_denied", "Locked Book", ItemStatus.PUBLISHED);
		CatalogueItem archived = item("item_archived", "Withdrawn Title", ItemStatus.ARCHIVED);

		when(catalogueItemRepository.findAllById(any()))
				.thenReturn(List.of(allowed, deniedItem, archived));
		when(entitlementQuery.check(SUBJECT, "item_allowed")).thenReturn(entitled(2));
		when(entitlementQuery.check(SUBJECT, "item_denied")).thenReturn(denied(DenyReason.NO_ENTITLEMENT));

		BatchItemsResponse response = service.batch(SUBJECT,
				new BatchItemsRequest(List.of("item_allowed", "item_denied", "item_archived", "item_missing")));

		assertThat(response.items()).extracting(BatchItem::id).containsExactly("item_allowed");
		assertThat(response.denied()).containsExactly("item_denied");
		// notFound covers both a genuinely unknown id and an archived one - the contract treats
		// them the same, so an archived title cannot be probed for by whether it comes back denied.
		assertThat(response.notFound()).containsExactlyInAnyOrder("item_archived", "item_missing");
	}

	@Test
	void moreThan100IdsIsRejectedBeforeAnyLookup() {
		List<String> tooMany = java.util.stream.IntStream.range(0, 101).mapToObj(i -> "item_" + i).toList();

		assertThatThrownBy(() -> service.batch(SUBJECT, new BatchItemsRequest(tooMany)))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.TOO_MANY_IDS));

		verify(catalogueItemRepository, never()).findAllById(any());
		verify(entitlementQuery, never()).check(any(), any());
	}

	@Test
	void mapsFieldsAndDerivesCopiesAndSearchIndexFromTheirOwnSources() {
		CatalogueItem source = item("item_1", "Rights for Robots", ItemStatus.PUBLISHED);
		source.setAuthors(List.of("Joshua C. Gellers"));
		source.setCoverUrl("https://cdn.tf/covers/item_1.jpg");
		source.setIsbn("9780367211745");
		source.setContentType(ContentType.PDF);
		source.setAccessTier(AccessTier.ELITE);
		CatalogueItem.Asset withIndex = new CatalogueItem.Asset();
		withIndex.setHasSearchIndex(true);
		source.setAssets(List.of(withIndex));

		when(catalogueItemRepository.findAllById(any())).thenReturn(List.of(source));
		when(entitlementQuery.check(SUBJECT, "item_1")).thenReturn(entitled(2));

		BatchItem result = service.batch(SUBJECT, new BatchItemsRequest(List.of("item_1"))).items().get(0);

		assertThat(result.id()).isEqualTo("item_1");
		assertThat(result.title()).isEqualTo("Rights for Robots");
		assertThat(result.authors()).containsExactly("Joshua C. Gellers");
		assertThat(result.coverUrl()).isEqualTo("https://cdn.tf/covers/item_1.jpg");
		assertThat(result.isbn()).isEqualTo("9780367211745");
		assertThat(result.contentType()).isEqualTo(ContentType.PDF);
		assertThat(result.accessTier()).isEqualTo(AccessTier.ELITE);
		// totalCopies is the caller's entitlement grant, not a property of the book itself.
		assertThat(result.totalCopies()).isEqualTo(2);
		assertThat(result.hasSearchIndex()).isTrue();
	}

	private static CatalogueItem item(String id, String title, ItemStatus status) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setTitle(title);
		item.setStatus(status);
		return item;
	}

	private static EntitlementDecision entitled(int copies) {
		return new EntitlementDecision(true, AccessLevel.ENTITLED_CONCURRENT, "ent_1", copies, 21, null, null);
	}

	private static EntitlementDecision denied(DenyReason reason) {
		return new EntitlementDecision(false, null, null, null, 0, null, reason);
	}

}
