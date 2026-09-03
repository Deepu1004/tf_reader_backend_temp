package com.tf.reader.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.admin.dto.CatalogueItemWrite;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.CatalogueItemSearchRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.security.TokenClaims;
import com.tf.reader.admin.service.CatalogueItemAdminService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/** Business rules for the four catalogue item admin operations, tested without a servlet or a database. */
class CatalogueItemAdminServiceTest {

	private CatalogueItemRepository catalogueItemRepository;
	private CatalogueItemSearchRepository searchRepository;
	private PublisherRepository publisherRepository;
	private EntitlementRepository entitlementRepository;
	private CatalogueVersionBumper versionBumper;
	private AdminAuditWriter auditWriter;

	private CatalogueItemAdminService service;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		catalogueItemRepository = mock(CatalogueItemRepository.class);
		searchRepository = mock(CatalogueItemSearchRepository.class);
		publisherRepository = mock(PublisherRepository.class);
		entitlementRepository = mock(EntitlementRepository.class);
		versionBumper = mock(CatalogueVersionBumper.class);
		auditWriter = mock(AdminAuditWriter.class);

		service = new CatalogueItemAdminService(catalogueItemRepository, searchRepository, publisherRepository,
				entitlementRepository, versionBumper, auditWriter, new AdminScopeAuthorizer());

		actingAs(AdminRole.SUPER_ADMIN, null, null);
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void actingAs(AdminRole role, String scopePublisherId) {
		actingAs(role, scopePublisherId, null);
	}

	private static void actingAs(AdminRole role, String scopePublisherId, String scopeInstitutionId) {
		Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(900)).claim(TokenClaims.ROLE, role.name());
		if (scopePublisherId != null) {
			builder.claim(TokenClaims.SCOPE_PUBLISHER_ID, scopePublisherId);
		}
		if (scopeInstitutionId != null) {
			builder.claim(TokenClaims.SCOPE_INSTITUTION_ID, scopeInstitutionId);
		}
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(builder.build(), null, "ROLE_ADMIN"));
	}

	private static CatalogueItemWrite pdfWrite(String publisherId) {
		return new CatalogueItemWrite(publisherId, List.of(), "Rights for Robots", null, List.of("Joshua Gellers"),
				List.of(), List.of(), null, ContentType.PDF, AccessTier.ELITE, List.of("Law"), "en", null, null, null,
				null, null);
	}

	private static CatalogueItemWrite audioWrite(String publisherId, Integer duration) {
		return new CatalogueItemWrite(publisherId, List.of(), "Robots, Read Aloud", null, List.of(), List.of(),
				List.of("A Narrator"), null, ContentType.AUDIO, AccessTier.SUBSCRIPTION, List.of(), "en", null, null,
				duration, null, null);
	}

	// ---------------------------------------------------------------- create

	@Test
	@DisplayName("create saves a book with contentState NONE and emits CREATE audit")
	void createSavesWithContentStateNone() {
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.create(pdfWrite("pub_rtlg"));

		assertThat(view.contentState()).isEqualTo(ContentState.NONE);
		assertThat(view.status()).isEqualTo(ItemStatus.DRAFT);
		verify(auditWriter).record(any(), eq(AuditLog.Action.CREATE), eq("CATALOGUE_ITEM"), any(), eq(null), any());
	}

	@Test
	@DisplayName("create AUDIO without duration throws VALIDATION_FAILED")
	void createAudioWithoutDurationThrows() {
		assertThatThrownBy(() -> service.create(audioWrite("pub_rtlg", null))).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(catalogueItemRepository, never()).save(any());
	}

	@Test
	@DisplayName("create PDF with a duration throws VALIDATION_FAILED")
	void createNonAudioWithDurationThrows() {
		CatalogueItemWrite write = new CatalogueItemWrite("pub_rtlg", List.of(), "Title", null, List.of(), List.of(),
				List.of(), null, ContentType.PDF, AccessTier.ELITE, List.of(), "en", null, null, 100, null, null);

		assertThatThrownBy(() -> service.create(write)).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	@DisplayName("create against an unknown publisher becomes 400, never a 500")
	void createUnknownPublisherIsValidationFailed() {
		when(catalogueItemRepository.save(any()))
				.thenThrow(new IllegalArgumentException("CatalogueItem.publisherId does not reference an existing publisher"));

		assertThatThrownBy(() -> service.create(pdfWrite("does-not-exist"))).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	@DisplayName("create with status PUBLISHED bumps the catalogue version")
	void createPublishedBumpsVersion() {
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		CatalogueItemWrite write = new CatalogueItemWrite("pub_rtlg", List.of(), "Title", null, List.of(), List.of(),
				List.of(), null, ContentType.PDF, AccessTier.ELITE, List.of(), "en", null, null, null, null,
				ItemStatus.PUBLISHED);

		var view = service.create(write);

		verify(versionBumper).bump(CatalogueVersionBumper.Scope.ITEM, view.id());
	}

	@Test
	@DisplayName("a publisher admin may not create a book for someone else's publisher")
	void publisherAdminCannotCreateForAnotherPublisher() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_mine");

		assertThatThrownBy(() -> service.create(pdfWrite("pub_other"))).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN_ROLE));
		verify(catalogueItemRepository, never()).save(any());
	}

	// ------------------------------------------------- duplicate ISBN (workstream 2)

	@Test
	@DisplayName("create with an ISBN nobody holds saves normally")
	void createsWhenIsbnIsNew() {
		when(catalogueItemRepository.findByIsbn("9780132350884")).thenReturn(Optional.empty());
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.create(isbnWrite("pub_rtlg", "9780132350884"));

		assertThat(view.isbn()).isEqualTo("9780132350884");
	}

	@Test
	@DisplayName("create with an ISBN another book already holds throws CODE_TAKEN")
	void rejectsCreateWhenIsbnAlreadyExists() {
		when(catalogueItemRepository.findByIsbn("9780132350884"))
				.thenReturn(Optional.of(isbnItem("item_42", "pub_rtlg", "9780132350884")));

		assertThatThrownBy(() -> service.create(isbnWrite("pub_rtlg", "9780132350884")))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN));
		verify(catalogueItemRepository, never()).save(any());
	}

	@Test
	@DisplayName("the duplicate check compares normalised ISBNs, so punctuation and case cannot slip a copy past it")
	void rejectsCreateWhenIsbnMatchesOnlyAfterNormalisation() {
		when(catalogueItemRepository.findByIsbn("012345678X"))
				.thenReturn(Optional.of(isbnItem("item_42", "pub_rtlg", "012345678X")));

		assertThatThrownBy(() -> service.create(isbnWrite("pub_rtlg", "0-123456 78x")))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN));
	}

	@Test
	@DisplayName("an absent ISBN is never a duplicate and is not even looked up")
	void allowsCreateWhenIsbnIsNull() {
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.create(pdfWrite("pub_rtlg"));
		service.create(pdfWrite("pub_rtlg"));

		verify(catalogueItemRepository, never()).findByIsbn(any());
	}

	@Test
	@DisplayName("update may keep the ISBN the item already holds")
	void allowsUpdateToKeepItsOwnIsbn() {
		CatalogueItem existing = isbnItem("item_42", "pub_rtlg", "9780132350884");
		when(catalogueItemRepository.findById("item_42")).thenReturn(Optional.of(existing));
		when(catalogueItemRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(existing));
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.update("item_42", isbnWrite("pub_rtlg", "9780132350884"));

		assertThat(view.isbn()).isEqualTo("9780132350884");
	}

	@Test
	@DisplayName("update may not take an ISBN a different book already holds")
	void rejectsUpdateWhenIsbnBelongsToAnotherItem() {
		when(catalogueItemRepository.findById("item_99"))
				.thenReturn(Optional.of(isbnItem("item_99", "pub_rtlg", null)));
		when(catalogueItemRepository.findByIsbn("9780132350884"))
				.thenReturn(Optional.of(isbnItem("item_42", "pub_rtlg", "9780132350884")));

		assertThatThrownBy(() -> service.update("item_99", isbnWrite("pub_rtlg", "9780132350884")))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN));
		verify(catalogueItemRepository, never()).save(any());
	}

	@Test
	@DisplayName("the refusal names the existing book only when the caller could already read it")
	void doesNotNameAnotherPublishersBookInTheRefusal() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_mine");
		when(catalogueItemRepository.findByIsbn("9780132350884"))
				.thenReturn(Optional.of(isbnItem("item_42", "pub_other", "9780132350884")));

		assertThatThrownBy(() -> service.create(isbnWrite("pub_mine", "9780132350884")))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN))
				.hasMessageNotContaining("item_42")
				.hasMessageNotContaining("pub_other");
	}

	// ---------------------------------------------------------------- get

	@Test
	@DisplayName("get unknown item throws NOT_FOUND")
	void getUnknownThrows() {
		when(catalogueItemRepository.findById("item_nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get("item_nope")).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("get returns full detail including assets and the joined publisherName")
	void getReturnsFullDetail() {
		CatalogueItem item = pdfItem("item_42", "pub_rtlg");
		CatalogueItem.Asset asset = new CatalogueItem.Asset();
		asset.setFormat(ContentType.PDF);
		asset.setMimeType("application/pdf");
		item.setAssets(List.of(asset));
		when(catalogueItemRepository.findById("item_42")).thenReturn(Optional.of(item));
		when(publisherRepository.findById("pub_rtlg"))
				.thenReturn(Optional.of(publisher("pub_rtlg", "Routledge")));

		var view = service.get("item_42");

		assertThat(view.publisherName()).isEqualTo("Routledge");
		assertThat(view.assets()).hasSize(1);
	}

	@Test
	@DisplayName("a publisher admin cannot read another publisher's book")
	void publisherAdminCannotReadAnotherPublishersBook() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_mine");
		when(catalogueItemRepository.findById("item_42")).thenReturn(Optional.of(pdfItem("item_42", "pub_other")));

		assertThatThrownBy(() -> service.get("item_42")).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN_ROLE));
	}

	// ---------------------------------------------------------------- update

	@Test
	@DisplayName("update never touches numberOfPages or contentState")
	void updateLeavesIngestOwnedFieldsAlone() {
		CatalogueItem existing = pdfItem("item_42", "pub_rtlg");
		existing.setNumberOfPages(240);
		existing.setContentState(ContentState.READY);
		when(catalogueItemRepository.findById("item_42")).thenReturn(Optional.of(existing));
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var view = service.update("item_42", pdfWrite("pub_rtlg"));

		assertThat(view.numberOfPages()).isEqualTo(240);
		assertThat(view.contentState()).isEqualTo(ContentState.READY);
		verify(auditWriter).record(any(), eq(AuditLog.Action.UPDATE), eq("CATALOGUE_ITEM"), any(), any(), any());
	}

	@Test
	@DisplayName("update to ARCHIVED bumps the catalogue version")
	void updateToArchivedBumpsVersion() {
		CatalogueItem existing = pdfItem("item_42", "pub_rtlg");
		when(catalogueItemRepository.findById("item_42")).thenReturn(Optional.of(existing));
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		CatalogueItemWrite write = new CatalogueItemWrite("pub_rtlg", List.of(), "Title", null, List.of(), List.of(),
				List.of(), null, ContentType.PDF, AccessTier.ELITE, List.of(), "en", null, null, null, null,
				ItemStatus.ARCHIVED);

		service.update("item_42", write);

		verify(versionBumper).bump(CatalogueVersionBumper.Scope.ITEM, "item_42");
	}

	@Test
	@DisplayName("update to DRAFT does not bump the catalogue version")
	void updateToDraftDoesNotBump() {
		CatalogueItem existing = pdfItem("item_42", "pub_rtlg");
		when(catalogueItemRepository.findById("item_42")).thenReturn(Optional.of(existing));
		when(catalogueItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.update("item_42", pdfWrite("pub_rtlg"));

		verify(versionBumper, never()).bump(any(), any());
	}

	@Test
	@DisplayName("update on an unknown item throws NOT_FOUND")
	void updateUnknownThrows() {
		when(catalogueItemRepository.findById("item_nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update("item_nope", pdfWrite("pub_rtlg"))).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	// ---------------------------------------------------------------- list

	@Test
	@DisplayName("list page=-1 throws VALIDATION_FAILED")
	void listNegativePageThrows() {
		assertThatThrownBy(() -> service.list(null, null, null, null, null, null, -1, null, null))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	@DisplayName("a publisher admin's list is pinned to their own publisherId")
	void publisherAdminListIsPinnedToOwnScope() {
		when(searchRepository.search(eq("pub_mine"), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(), 0));

		service.list("pub_mine", null, null, null, null, null, null, null, null);

		verify(searchRepository).search(eq("pub_mine"), any(), any(), any(), any(), eq(0), eq(20));
	}

	@Test
	@DisplayName("a publisher admin asking for another publisher's items is denied")
	void publisherAdminAskingForAnotherPublisherIsDenied() {
		assertThatThrownBy(() -> service.list("pub_mine", "pub_other", null, null, null, null, null, null, null))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN_ROLE));
	}

	@Test
	@DisplayName("an institution admin sees entitlementStatus per item, resolved from one entitlement load")
	void institutionAdminListResolvesEntitlementStatus() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		CatalogueItem entitledItem = pdfItem("item_42", "pub_rtlg");
		CatalogueItem unrelatedItem = pdfItem("item_99", "pub_other");
		when(searchRepository.search(any(), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(entitledItem, unrelatedItem), 2));
		Entitlement activeGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.PUBLISHER, "pub_rtlg", null, 14,
				null, null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(activeGrant)));

		var result = service.list(null, null, null, null, null, null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("active");
		assertThat(result.items().get(1).entitlementStatus()).isEqualTo("none");
	}

	@Test
	@DisplayName("a super admin passing institutionId sees that institution's entitlementStatus view")
	void superAdminWithInstitutionIdSeesThatInstitutionsView() {
		CatalogueItem item = pdfItem("item_42", "pub_rtlg");
		when(searchRepository.search(any(), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(item), 1));
		Entitlement activeGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.PUBLISHER, "pub_rtlg", null, 14,
				null, null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(activeGrant)));

		var result = service.list(null, null, null, null, null, null, null, null, "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("active");
	}

	@Test
	@DisplayName("a publisher admin's list has no entitlementStatus, not \"none\"")
	void publisherAdminListOmitsEntitlementStatus() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_rtlg", null);
		CatalogueItem item = pdfItem("item_42", "pub_rtlg");
		when(searchRepository.search(eq("pub_rtlg"), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(item), 1));

		var result = service.list("pub_rtlg", null, null, null, null, null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isNull();
	}

	@Test
	@DisplayName("a publisher admin passing institutionId still gets no entitlementStatus")
	void publisherAdminInstitutionIdIsIgnored() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_rtlg", null);
		CatalogueItem item = pdfItem("item_42", "pub_rtlg");
		when(searchRepository.search(eq("pub_rtlg"), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(item), 1));

		var result = service.list("pub_rtlg", null, null, null, null, null, null, null, "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isNull();
		verify(entitlementRepository, never()).findByInstitutionId(any(), any());
	}

	@Test
	@DisplayName("an institution admin cannot see another institution's entitlementStatus by passing its id")
	void institutionAdminCannotSpoofAnotherInstitution() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		CatalogueItem item = pdfItem("item_42", "pub_rtlg");
		when(searchRepository.search(any(), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(item), 1));
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

		var result = service.list(null, null, null, null, null, null, null, null, "inst_other");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("none");
		verify(entitlementRepository, never()).findByInstitutionId(eq("inst_other"), any());
	}

	@Test
	@DisplayName("the strongest matching entitlement status wins across scopes")
	void strongestEntitlementStatusWinsAcrossScopes() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		CatalogueItem item = new CatalogueItem();
		item.setId("item_42");
		item.setPublisherId("pub_rtlg");
		item.setCollectionIds(List.of("col_law2024"));
		when(searchRepository.search(any(), any(), any(), any(), any(), eq(0), eq(20)))
				.thenReturn(new CatalogueItemSearchRepository.Results(List.of(item), 1));
		Entitlement pendingItemGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.ITEM, "item_42", null, 14, null,
				null, EntitlementStatus.PENDING, 0, null, null);
		Entitlement activePublisherGrant = new Entitlement("ent_2", "inst_7f3", ScopeType.PUBLISHER, "pub_rtlg", null,
				14, null, null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(
						List.of(pendingItemGrant, activePublisherGrant)));

		var result = service.list(null, null, null, null, null, null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("active");
	}

	// ---------------------------------------------------------------- fixtures

	private static CatalogueItemWrite isbnWrite(String publisherId, String isbn) {
		return new CatalogueItemWrite(publisherId, List.of(), "Rights for Robots", null, List.of("Joshua Gellers"),
				List.of(), List.of(), isbn, ContentType.PDF, AccessTier.ELITE, List.of("Law"), "en", null, null, null,
				null, null);
	}

	private static CatalogueItem isbnItem(String id, String publisherId, String isbn) {
		CatalogueItem item = pdfItem(id, publisherId);
		item.setIsbn(isbn);
		return item;
	}

	private static CatalogueItem pdfItem(String id, String publisherId) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setPublisherId(publisherId);
		item.setTitle("Rights for Robots");
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.ELITE);
		item.setStatus(ItemStatus.DRAFT);
		item.setContentState(ContentState.NONE);
		return item;
	}

	private static com.tf.reader.catalogue.entity.Publisher publisher(String id, String name) {
		com.tf.reader.catalogue.entity.Publisher p = new com.tf.reader.catalogue.entity.Publisher();
		p.setName(name);
		try {
			var idField = com.tf.reader.catalogue.entity.Publisher.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(p, id);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		return p;
	}

}
