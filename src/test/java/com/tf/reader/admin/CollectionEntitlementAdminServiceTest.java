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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.CollectionEntitlementAdminService;
import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.security.TokenClaims;

/** Business rules for GET /api/admin/v1/collections, tested without a servlet or a database. */
class CollectionEntitlementAdminServiceTest {

	private BookCollectionRepository bookCollectionRepository;
	private EntitlementRepository entitlementRepository;

	private CollectionEntitlementAdminService service;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		bookCollectionRepository = mock(BookCollectionRepository.class);
		entitlementRepository = mock(EntitlementRepository.class);

		service = new CollectionEntitlementAdminService(bookCollectionRepository, entitlementRepository,
				new AdminScopeAuthorizer());

		actingAs(AdminRole.SUPER_ADMIN, null, null);
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
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

	private static BookCollection collection(String id, String publisherId) {
		return new BookCollection(id, publisherId, "LAW2024", "Law Essentials", "desc");
	}

	private static Page<BookCollection> pageOf(BookCollection... collections) {
		return new PageImpl<>(List.of(collections));
	}

	// ---------------------------------------------------------------- list

	@Test
	@DisplayName("list page=-1 throws VALIDATION_FAILED")
	void listNegativePageThrows() {
		assertThatThrownBy(() -> service.list(null, null, -1, null, null)).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	@DisplayName("list size=0 throws VALIDATION_FAILED")
	void listSizeTooSmallThrows() {
		assertThatThrownBy(() -> service.list(null, null, null, 0, null)).isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	@DisplayName("a publisher admin's list is pinned to their own publisherId")
	void publisherAdminListIsPinnedToOwnScope() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_mine", null);
		when(bookCollectionRepository.findByPublisherId(eq("pub_mine"), any())).thenReturn(pageOf());

		service.list("pub_mine", null, null, null, null);

		verify(bookCollectionRepository).findByPublisherId(eq("pub_mine"), any());
	}

	@Test
	@DisplayName("a publisher admin asking for another publisher's collections is denied")
	void publisherAdminAskingForAnotherPublisherIsDenied() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_mine", null);

		assertThatThrownBy(() -> service.list("pub_mine", "pub_other", null, null, null))
				.isInstanceOf(ApiException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.FORBIDDEN_ROLE));
	}

	@Test
	@DisplayName("a super admin with no publisherId sees every publisher's collections")
	void superAdminWithNoPublisherIdSeesEverything() {
		when(bookCollectionRepository.findAll(any(Pageable.class)))
				.thenReturn(pageOf(collection("col_law2024", "pub_rtlg")));

		var result = service.list(null, null, null, null, null);

		assertThat(result.items()).hasSize(1);
	}

	@Test
	@DisplayName("an institution admin sees entitlementStatus per collection, resolved from one entitlement load")
	void institutionAdminListResolvesEntitlementStatus() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		BookCollection entitledCollection = collection("col_law2024", "pub_rtlg");
		BookCollection unrelatedCollection = collection("col_env2024", "pub_crc");
		when(bookCollectionRepository.findAll(any(Pageable.class)))
				.thenReturn(pageOf(entitledCollection, unrelatedCollection));
		Entitlement activeGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.COLLECTION, "col_law2024", null, 14,
				null, null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new PageImpl<>(List.of(activeGrant)));

		var result = service.list(null, null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("ACTIVE");
		assertThat(result.items().get(1).entitlementStatus()).isEqualTo("NONE");
	}

	@Test
	@DisplayName("a super admin passing institutionId sees that institution's entitlementStatus view")
	void superAdminWithInstitutionIdSeesThatInstitutionsView() {
		when(bookCollectionRepository.findAll(any(Pageable.class)))
				.thenReturn(pageOf(collection("col_law2024", "pub_rtlg")));
		Entitlement activeGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.PUBLISHER, "pub_rtlg", null, 14,
				null, null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new PageImpl<>(List.of(activeGrant)));

		var result = service.list(null, null, null, null, "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("a publisher admin's list has no entitlementStatus, not \"none\"")
	void publisherAdminListOmitsEntitlementStatus() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_rtlg", null);
		when(bookCollectionRepository.findByPublisherId(eq("pub_rtlg"), any()))
				.thenReturn(pageOf(collection("col_law2024", "pub_rtlg")));

		var result = service.list("pub_rtlg", null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isNull();
	}

	@Test
	@DisplayName("a publisher admin passing institutionId still gets no entitlementStatus")
	void publisherAdminInstitutionIdIsIgnored() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_rtlg", null);
		when(bookCollectionRepository.findByPublisherId(eq("pub_rtlg"), any()))
				.thenReturn(pageOf(collection("col_law2024", "pub_rtlg")));

		var result = service.list("pub_rtlg", null, null, null, "inst_7f3");

		assertThat(result.items().get(0).entitlementStatus()).isNull();
		verify(entitlementRepository, never()).findByInstitutionId(any(), any());
	}

	@Test
	@DisplayName("an institution admin cannot see another institution's entitlementStatus by passing its id")
	void institutionAdminCannotSpoofAnotherInstitution() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		when(bookCollectionRepository.findAll(any(Pageable.class)))
				.thenReturn(pageOf(collection("col_law2024", "pub_rtlg")));
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any())).thenReturn(new PageImpl<>(List.of()));

		var result = service.list(null, null, null, null, "inst_other");

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("NONE");
		verify(entitlementRepository, never()).findByInstitutionId(eq("inst_other"), any());
	}

	@Test
	@DisplayName("an ITEM-scoped entitlement never counts toward a collection's status")
	void itemScopedEntitlementIsIgnored() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		BookCollection col = collection("col_law2024", "pub_rtlg");
		when(bookCollectionRepository.findAll(any(Pageable.class))).thenReturn(pageOf(col));
		// Scoped to an item id that happens to equal the collection's id - must not match.
		Entitlement itemGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.ITEM, "col_law2024", null, 14, null,
				null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new PageImpl<>(List.of(itemGrant)));

		var result = service.list(null, null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("NONE");
	}

	@Test
	@DisplayName("the strongest matching entitlement status wins across COLLECTION and PUBLISHER scopes")
	void strongestEntitlementStatusWinsAcrossScopes() {
		actingAs(AdminRole.INSTITUTION_ADMIN, null, "inst_7f3");
		BookCollection col = collection("col_law2024", "pub_rtlg");
		when(bookCollectionRepository.findAll(any(Pageable.class))).thenReturn(pageOf(col));
		Entitlement pendingCollectionGrant = new Entitlement("ent_1", "inst_7f3", ScopeType.COLLECTION, "col_law2024",
				null, 14, null, null, EntitlementStatus.PENDING, 0, null, null);
		Entitlement activePublisherGrant = new Entitlement("ent_2", "inst_7f3", ScopeType.PUBLISHER, "pub_rtlg", null,
				14, null, null, EntitlementStatus.ACTIVE, 0, null, null);
		when(entitlementRepository.findByInstitutionId(eq("inst_7f3"), any()))
				.thenReturn(new PageImpl<>(List.of(pendingCollectionGrant, activePublisherGrant)));

		var result = service.list(null, null, null, null, null);

		assertThat(result.items().get(0).entitlementStatus()).isEqualTo("ACTIVE");
	}

}
