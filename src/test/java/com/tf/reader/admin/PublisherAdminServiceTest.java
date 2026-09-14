package com.tf.reader.admin;

import com.tf.reader.admin.dto.PublisherView;
import com.tf.reader.admin.dto.PublisherWrite;
import com.tf.reader.admin.dto.StatusChange;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.PublisherAdminService;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.common.security.TokenClaims;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Business rules for the five publisher admin operations, tested without a
 * servlet or a database.
 */
class PublisherAdminServiceTest {

	private static final Instant T = Instant.parse("2026-08-10T09:00:00Z");

	private PublisherRepository publisherRepository;
	private CatalogueItemRepository catalogueItemRepository;
	private BookCollectionRepository bookCollectionRepository;
	private EntitlementRepository entitlementRepository;
	private CatalogueVersionBumper versionBumper;
	private AdminAuditWriter auditWriter;
	private MongoTemplate mongo;

	private PublisherAdminService service;

	@BeforeEach
	void setUp() {
		publisherRepository = mock(PublisherRepository.class);
		catalogueItemRepository = mock(CatalogueItemRepository.class);
		bookCollectionRepository = mock(BookCollectionRepository.class);
		entitlementRepository = mock(EntitlementRepository.class);
		versionBumper = mock(CatalogueVersionBumper.class);
		auditWriter = mock(AdminAuditWriter.class);
		mongo = mock(MongoTemplate.class);

		service = new PublisherAdminService(publisherRepository, catalogueItemRepository, bookCollectionRepository,
				entitlementRepository, versionBumper, auditWriter, mongo, new AdminScopeAuthorizer());

		actingAs(AdminRole.SUPER_ADMIN, null);
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	private static void actingAs(AdminRole role, String publisherId) {
		actingAs(role, publisherId, null);
	}

	private static void actingAs(AdminRole role, String publisherId, String institutionId) {
		Jwt.Builder tokenBuilder = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("adm_test")
				.claim(TokenClaims.ROLE, role.name())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600));
		if (publisherId != null) {
			tokenBuilder.claim(TokenClaims.SCOPE_PUBLISHER_ID, publisherId);
		}
		if (institutionId != null) {
			tokenBuilder.claim(TokenClaims.SCOPE_INSTITUTION_ID, institutionId);
		}
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(tokenBuilder.build(), null, "ROLE_ADMIN"));
	}

	private static Publisher routledge() {
		Publisher p = new Publisher();
		p.setCode("routledge"); // setCode normalises to upper-case
		p.setName("Routledge");
		p.setDescription("Academic imprint");
		p.setLogoUrl("https://cdn.tf/logos/routledge.png");
		p.setStatus(RecordStatus.ACTIVE);
		p.setCreatedAt(T);
		p.setUpdatedAt(T);
		// simulate the id that Mongo would assign
		try {
			var idField = Publisher.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(p, "pub_r1");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return p;
	}

	// ---------------------------------------------------------------- create

	@Test
	@DisplayName("create saves a publisher with ACTIVE status and emits CREATE audit")
	void createSavesAndAudits() {
		when(publisherRepository.findByCode("ROUTLEDGE")).thenReturn(Optional.empty());
		when(publisherRepository.save(any())).thenReturn(routledge());
		when(catalogueItemRepository.countByPublisherId(any())).thenReturn(0L);
		when(bookCollectionRepository.countByPublisherId(any())).thenReturn(0L);

		PublisherWrite write = new PublisherWrite("routledge", "Routledge", "Academic imprint",
				"https://cdn.tf/logos/routledge.png");
		PublisherView view = service.create(write);

		assertThat(view.status()).isEqualTo(RecordStatus.ACTIVE);
		assertThat(view.code()).isEqualTo("ROUTLEDGE");

		ArgumentCaptor<AuditLog.Action> actionCaptor = ArgumentCaptor.forClass(AuditLog.Action.class);
		verify(auditWriter).record(any(), actionCaptor.capture(), eq("PUBLISHER"), any(), eq(null), any());
		assertThat(actionCaptor.getValue()).isEqualTo(AuditLog.Action.CREATE);
	}

	@Test
	@DisplayName("create with a duplicate code throws CODE_TAKEN")
	void createDuplicateCodeThrows() {
		when(publisherRepository.findByCode("ROUTLEDGE")).thenReturn(Optional.of(routledge()));

		PublisherWrite write = new PublisherWrite("routledge", "Routledge", null, null);

		assertThatThrownBy(() -> service.create(write)).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN));

		verify(publisherRepository, never()).save(any());
	}

	@Test
	@DisplayName("create is refused for a non-super admin before anything is read")
	void createRequiresSuperAdmin() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");

		PublisherWrite write = new PublisherWrite("routledge", "Routledge", null, null);

		assertThatThrownBy(() -> service.create(write)).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN_ROLE));

		verify(publisherRepository, never()).findByCode(any());
		verify(publisherRepository, never()).save(any());
	}

	// ---------------------------------------------------------------- get

	@Test
	@DisplayName("get unknown publisher throws NOT_FOUND")
	void getUnknownThrows() {
		when(publisherRepository.findById("pub_nope")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get("pub_nope")).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("get returns derived counts from the two repositories")
	void getDerivedCounts() {
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		when(catalogueItemRepository.countByPublisherId("pub_r1")).thenReturn(17L);
		when(bookCollectionRepository.countByPublisherId("pub_r1")).thenReturn(4L);

		PublisherView view = service.get("pub_r1");

		assertThat(view.itemCount()).isEqualTo(17);
		assertThat(view.collectionCount()).isEqualTo(4);
	}

	// ---------------------------------------------------------------- update

	@Test
	@DisplayName("update applies new fields and emits UPDATE audit")
	void updateAppliesFieldsAndAudits() {
		Publisher existing = routledge();
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(existing));
		when(publisherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(catalogueItemRepository.countByPublisherId(any())).thenReturn(0L);
		when(bookCollectionRepository.countByPublisherId(any())).thenReturn(0L);

		PublisherWrite write = new PublisherWrite("routledge", "Routledge Ltd", null, null);
		PublisherView view = service.update("pub_r1", write);

		assertThat(view.name()).isEqualTo("Routledge Ltd");

		verify(auditWriter).record(any(), eq(AuditLog.Action.UPDATE), eq("PUBLISHER"), any(), any(), any());
	}

	// ---------------------------------------------------------------- status

	@Test
	@DisplayName("changeStatus to SUSPENDED emits STATUS audit with reason in meta and bumps version")
	void changeStatusSuspendedAuditsAndBumps() {
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		when(publisherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(catalogueItemRepository.countByPublisherId(any())).thenReturn(0L);
		when(bookCollectionRepository.countByPublisherId(any())).thenReturn(0L);

		service.changeStatus("pub_r1", new StatusChange(RecordStatus.SUSPENDED, "contract under review"));

		ArgumentCaptor<java.util.Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(java.util.Map.class);
		verify(auditWriter).record(any(), eq(AuditLog.Action.STATUS), eq("PUBLISHER"), any(), any(), any(),
				metaCaptor.capture());
		assertThat(metaCaptor.getValue()).containsEntry("reason", "contract under review");

		verify(versionBumper).bump(CatalogueVersionBumper.Scope.PUBLISHER, "pub_r1");
	}

	@Test
	@DisplayName("changeStatus to RETIRED does not bump catalogue version")
	void changeStatusRetiredDoesNotBump() {
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		when(publisherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(catalogueItemRepository.countByPublisherId(any())).thenReturn(0L);
		when(bookCollectionRepository.countByPublisherId(any())).thenReturn(0L);

		service.changeStatus("pub_r1", new StatusChange(RecordStatus.RETIRED, null));

		verify(versionBumper, never()).bump(any(), any());
	}

	@Test
	@DisplayName("changeStatus to ACTIVE bumps catalogue version")
	void changeStatusActiveAlsoBumps() {
		Publisher suspended = routledge();
		suspended.setStatus(RecordStatus.SUSPENDED);
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(suspended));
		when(publisherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(catalogueItemRepository.countByPublisherId(any())).thenReturn(0L);
		when(bookCollectionRepository.countByPublisherId(any())).thenReturn(0L);

		service.changeStatus("pub_r1", new StatusChange(RecordStatus.ACTIVE, null));

		verify(versionBumper).bump(CatalogueVersionBumper.Scope.PUBLISHER, "pub_r1");
	}

	// ---------------------------------------------------------------- list

	// Page/size bounds are validated at the edge by PageQueryArgumentResolver, not here -
	// see PageQueryArgumentResolverTest.

	@Test
	@DisplayName("list returns the page and size it was asked for")
	void listReturnsThePageRequested() {
		stubOnePublisherPage();

		PageResponse<PublisherView> result = service.list(null, null, new PageQuery(0, 20), null);

		assertThat(result.page()).isEqualTo(0);
		assertThat(result.size()).isEqualTo(20);
		assertThat(result.total()).isEqualTo(1);
	}

	// -------------------------------------------------- list: entitlementStatus (workstream 3)

	@Test
	@DisplayName("a publisher-scoped entitlement tags the publisher active")
	void tagsPublisherWithActiveWhenInstitutionHasPublisherEntitlement() {
		stubOnePublisherPage();
		stubEntitlements(entitlement(ScopeType.PUBLISHER, "pub_r1", EntitlementStatus.ACTIVE));

		var result = service.list(null, null, new PageQuery(0, 20), "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("an institution with no matching grant sees \"none\", not null")
	void tagsPublisherWithNoneWhenInstitutionHasNoEntitlement() {
		stubOnePublisherPage();
		stubEntitlements();

		var result = service.list(null, null, new PageQuery(0, 20), "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("NONE");
	}

	@Test
	@DisplayName("a caller who is not viewing as an institution sees null, not \"none\"")
	void leavesEntitlementStatusNullWhenNoInstitutionView() {
		stubOnePublisherPage();

		var result = service.list(null, null, new PageQuery(0, 20), null);

		assertThat(result.items().get(0).entitlementStatus()).isNull();
		verify(entitlementRepository, never()).findByInstitutionId(any(), any());
	}

	@Test
	@DisplayName("a publisher's status is never aggregated from its collections or books")
	void ignoresCollectionAndItemScopedEntitlements() {
		stubOnePublisherPage();
		stubEntitlements(entitlement(ScopeType.COLLECTION, "col_law2024", EntitlementStatus.ACTIVE),
				entitlement(ScopeType.ITEM, "item_42", EntitlementStatus.ACTIVE));

		var result = service.list(null, null, new PageQuery(0, 20), "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("NONE");
	}

	@Test
	@DisplayName("the strongest matching entitlement status wins")
	void strongestStatusWinsAcrossOverlappingEntitlements() {
		stubOnePublisherPage();
		stubEntitlements(entitlement(ScopeType.PUBLISHER, "pub_r1", EntitlementStatus.REVOKED),
				entitlement(ScopeType.PUBLISHER, "pub_r1", EntitlementStatus.ACTIVE));

		var result = service.list(null, null, new PageQuery(0, 20), "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("an institution admin sees their own institution, never one they pass in")
	void institutionAdminCannotSpoofAnotherInstitution() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		stubOnePublisherPage();
		stubEntitlements(entitlement(ScopeType.PUBLISHER, "pub_r1", EntitlementStatus.ACTIVE));

		var result = service.list(null, null, new PageQuery(0, 20), "inst_someone_else");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("ACTIVE");
		verify(entitlementRepository, never()).findByInstitutionId(eq("inst_someone_else"), any());
	}

	// -------------------------------------------------- list: who may call it (workstream 3)

	/**
	 * The anti-enumeration guard that restricting this endpoint to SUPER_ADMIN used to provide.
	 * Widening the roles is only safe because of this filter, so assert on the query itself rather
	 * than on the returned rows - a stubbed MongoTemplate returns whatever it is told to, so
	 * checking the results would pass even if the criteria were never applied.
	 */
	@Test
	@DisplayName("a publisher admin's list is pinned to their own publisher")
	void publisherAdminSeesOnlyTheirOwnPublisher() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");
		stubOnePublisherPage();

		service.list(null, null, new PageQuery(0, 20), null);

		ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
		verify(mongo).find(query.capture(), eq(Publisher.class));
		assertThat(query.getValue().getQueryObject().toJson()).contains("pub_r1");
	}

	@Test
	@DisplayName("a publisher admin gets no entitlementStatus even when an institutionId is passed")
	void publisherAdminHasNoInstitutionView() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");
		stubOnePublisherPage();

		var result = service.list(null, null, new PageQuery(0, 20), "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isNull();
		verify(entitlementRepository, never()).findByInstitutionId(any(), any());
	}

	@Test
	@DisplayName("an admin with no recognised role is refused")
	void rejectsAnAdminWithNoRecognisedRole() {
		SecurityContextHolder.clearContext();

		assertThatThrownBy(() -> service.list(null, null, new PageQuery(0, 20), null))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN_ROLE));
	}

	// ---------------------------------------------------------------- list fixtures

	private void stubOnePublisherPage() {
		when(mongo.count(any(Query.class), eq(Publisher.class))).thenReturn(1L);
		when(mongo.find(any(Query.class), eq(Publisher.class))).thenReturn(List.of(routledge()));
	}

	private void stubEntitlements(Entitlement... entitlements) {
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new PageImpl<>(List.of(entitlements)));
	}

	private static Entitlement entitlement(ScopeType scopeType, String scopeId, EntitlementStatus status) {
		return new Entitlement(null, "inst_7f3", scopeType, scopeId, null, 14, null, null, status, 0, null, null);
	}
}
