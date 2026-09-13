package com.tf.reader.catalogue.opds;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.service.EntitlementAdminService;
import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.opds.controller.OpdsCatalogueController;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.security.TokenClaims;

/**
 * The feed cache is keyed on {@code catalogueVersion} and the feed is entitlement-personalised, so
 * revoking access has to move that number or a reader keeps being served a {@code 304} for books
 * they can no longer open. {@code shared.md} promises revocation takes effect on the next request;
 * this is the test behind that promise.
 *
 * <p>{@code OpdsCatalogueControllerTest} does not cover it. That test mocks {@link
 * com.tf.reader.catalogue.opds.service.OpdsFeedService} and hard-codes the expected tag, so it
 * proves the controller formats an ETag and short-circuits on a match - but it cannot fail if the
 * bumper stops being called, because no real institution or entitlement is involved.
 *
 * <p>Driven through the real controller against real Mongo rather than over HTTP: the chain being
 * tested is mutation to {@code catalogueVersion} to ETag, and the app resource-server chain is not
 * part of it. Going over HTTP would add app-audience token plumbing that tests nothing this test
 * is about.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class EntitlementChangeInvalidatesFeedCacheIT extends ContainerisedInfrastructure {

	private static final String INSTITUTION_ID = "inst_etagcache";
	private static final String PUBLISHER_ID = "pub_etagcache";

	@Autowired private OpdsCatalogueController opdsController;
	@Autowired private EntitlementAdminService entitlements;
	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private EntitlementRepository entitlementRepository;

	@AfterEach
	void cleanUp() {
		SecurityContextHolder.clearContext();
		entitlementRepository.deleteAll(entitlementRepository.findByScopeTypeAndScopeId(ScopeType.PUBLISHER,
				PUBLISHER_ID));
		catalogueItemRepository.deleteAll(catalogueItemRepository.findByPublisherIdAndStatus(PUBLISHER_ID,
				ItemStatus.PUBLISHED));
		institutionRepository.deleteById(INSTITUTION_ID);
		publisherRepository.deleteById(PUBLISHER_ID);
	}

	@Test
	@DisplayName("revoking an entitlement changes the ETag, so a cached feed is not served again")
	void revokingAnEntitlementInvalidatesTheCachedFeed() {
		Entitlement grant = seedEntitledInstitution();
		CurrentUser reader = new CurrentUser("reader-1", UserType.INSTITUTION, INSTITUTION_ID, List.of(), List.of());

		String firstEtag = requireEtag(opdsController.rootFeed(INSTITUTION_ID, reader, null));

		// Prove the cache is genuinely live before revoking. Without this, step three below would
		// pass just as happily in a world where ETags were never being emitted at all.
		ResponseEntity<?> cached = opdsController.rootFeed(INSTITUTION_ID, reader, firstEtag);
		assertThat(cached.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);

		asSuperAdmin();
		entitlements.revoke(grant.getId());

		ResponseEntity<?> afterRevoke = opdsController.rootFeed(INSTITUTION_ID, reader, firstEtag);
		assertThat(afterRevoke.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(requireEtag(afterRevoke)).isNotEqualTo(firstEtag);
	}

	private static String requireEtag(ResponseEntity<?> response) {
		String etag = response.getHeaders().getETag();
		assertThat(etag).as("every feed response carries an ETag").isNotNull();
		return etag;
	}

	private Entitlement seedEntitledInstitution() {
		Institution institution = new Institution();
		institution.setId(INSTITUTION_ID);
		institution.setCode("ETAGCACHE");
		institution.setName("ETag Cache Institution");
		institution.setType(InstitutionType.ACADEMIC);
		institution.setCountry("UK");
		institution.setStatus(RecordStatus.ACTIVE);
		institution.setCatalogueVersion(1L);
		institution.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		institutionRepository.save(institution);

		publisherRepository.save(new Publisher(PUBLISHER_ID, "ETAGCACHE", "ETag Cache Press", null, null,
				RecordStatus.ACTIVE, null, null));

		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(PUBLISHER_ID);
		item.setCollectionIds(List.of());
		item.setTitle("A Book Behind An Entitlement");
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.SUBSCRIPTION);
		item.setStatus(ItemStatus.PUBLISHED);
		item.setContentState(ContentState.READY);
		catalogueItemRepository.save(item);

		return entitlementRepository.save(new Entitlement(null, INSTITUTION_ID, ScopeType.PUBLISHER, PUBLISHER_ID,
				null, 14, null, null, EntitlementStatus.ACTIVE, 0, Instant.now(), Instant.now()));
	}

	private static void asSuperAdmin() {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("adm_test")
				.claim(TokenClaims.ROLE, AdminRole.SUPER_ADMIN.name())
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_ADMIN"));
	}

}
