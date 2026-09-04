package com.tf.reader.catalogue.opds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.Entitlement;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.FeedSettings;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.ScopeType;
import com.tf.reader.catalogue.entity.Shelf;
import com.tf.reader.catalogue.opds.dto.OpdsNavigationFeed;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationDocument;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsFeedService;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.FeedSettingsRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;

/**
 * Real Mongo, real {@code EntitlementQueryImpl}, no mocked repositories - per STYLE, anything
 * touching Mongo goes through Testcontainers rather than a mock. Covers {@link OpdsFeedService}
 * end to end: the root feed, a curated shelf and the {@code all} group.
 */
// tnf.seed.enabled explicit here, not just left to the test-wide default: the shared,
// never-reset Mongo container means any context that lets the seeder run writes rows
// (RTLG, inst_7f3...) that plain repository tests insert as their own fresh fixtures -
// see the comment on tnf.seed.enabled in src/test/resources/application.properties.
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class OpdsFeedServiceIT extends ContainerisedInfrastructure {

	@Autowired private OpdsFeedService feedService;
	@Autowired private InstitutionRepository institutionRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private FeedSettingsRepository feedSettingsRepository;
	@Autowired private EntitlementRepository entitlementRepository;

	private Institution newInstitution(String code) {
		return newInstitution(code, RecordStatus.ACTIVE);
	}

	private Institution newInstitution(String code, RecordStatus status) {
		Institution institution = new Institution();
		institution.setCode(code);
		institution.setName(code + " Institution");
		institution.setType(InstitutionType.ACADEMIC);
		institution.setCountry("UK");
		institution.setStatus(status);
		institution.setCatalogueVersion(1L);
		institution.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return institutionRepository.save(institution);
	}

	private Publisher newPublisher(String code) {
		return publisherRepository.save(
				new Publisher(null, code, code + " Press", null, null, RecordStatus.ACTIVE, null, null));
	}

	private CatalogueItem newItem(String publisherId, String title, AccessTier accessTier) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setCollectionIds(List.of());
		item.setTitle(title);
		item.setAccessTier(accessTier);
		item.setStatus(ItemStatus.PUBLISHED);
		item.setContentState(ContentState.READY);
		item.setContentType(ContentType.EPUB);
		item.setPublishedAt(LocalDate.of(2026, 1, 1));
		item.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(item);
	}

	private void grantEliteEntitlement(String institutionId, String itemId, int copies) {
		Entitlement entitlement = new Entitlement();
		entitlement.setInstitutionId(institutionId);
		entitlement.setScopeType(ScopeType.ITEM);
		entitlement.setScopeId(itemId);
		entitlement.setCopies(copies);
		entitlement.setLoanPeriodDays(14);
		entitlement.setStatus(EntitlementStatus.ACTIVE);
		entitlementRepository.save(entitlement);
	}

	private SubjectRef subjectFor(Institution institution) {
		return new SubjectRef("user_1", institution.getId());
	}

	// FeedSettingsPersistenceGuard rejects anything but exactly 3 shelves - fill in whichever
	// of shelf_1/shelf_2/shelf_3 a test does not care about with an empty placeholder.
	private void saveFeedSettings(String institutionId, Shelf... configured) {
		java.util.Map<String, Shelf> byId = new java.util.LinkedHashMap<>();
		for (Shelf shelf : configured) {
			byId.put(shelf.getId(), shelf);
		}
		List<Shelf> shelves = new java.util.ArrayList<>();
		int order = 1;
		for (String id : List.of("shelf_1", "shelf_2", "shelf_3")) {
			shelves.add(byId.getOrDefault(id, new Shelf(id, "Empty", order, List.of())));
			order++;
		}
		feedSettingsRepository.save(
				new FeedSettings(null, institutionId, "Feed", 20, "publishedAt.desc", shelves, null, 0));
	}

	// ------------------------------------------------------------------------- loadInstitution

	@Test
	void loadInstitutionReturnsAKnownInstitution() {
		Institution saved = newInstitution("OPDS-LOAD-OK");

		assertThat(feedService.loadInstitution(saved.getId()).getName()).isEqualTo(saved.getName());
	}

	@Test
	void loadInstitutionIs404ForAnUnknownId() {
		assertThatThrownBy(() -> feedService.loadInstitution("does-not-exist"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	/**
	 * NOT_FOUND, not a 403: a suspended institution has to be indistinguishable from one that was
	 * never there, or anyone who can type an id into a feed URL learns which institutions exist.
	 * Asserting the code rather than the message is the point of the test - a 403 here would be
	 * the disclosure this gate exists to prevent, and would still "pass" a status-only assertion.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "SUSPENDED", "RETIRED" })
	void loadInstitutionIs404WhenTheInstitutionIsNotActive(String status) {
		Institution inactive = newInstitution("OPDS-LOAD-" + status, RecordStatus.valueOf(status));

		assertThatThrownBy(() -> feedService.loadInstitution(inactive.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	// -------------------------------------------------------------------------------- root feed

	@Test
	void rootFeedEmbedsOnlyTheEntitledItemsFromACuratedShelf() {
		Institution institution = newInstitution("OPDS-ROOT-OK");
		Publisher publisher = newPublisher("OPDS-ROOT-PUB");
		CatalogueItem openAccessItem = newItem(publisher.getId(), "Free Book", AccessTier.OPEN_ACCESS);
		CatalogueItem unentitledItem = newItem(publisher.getId(), "Locked Book", AccessTier.SUBSCRIPTION);

		Shelf shelf = new Shelf("shelf_1", "New this term", 1,
				List.of(openAccessItem.getId(), unentitledItem.getId()));
		saveFeedSettings(institution.getId(), shelf);

		OpdsNavigationFeed feed = feedService.rootFeed(institution, subjectFor(institution));

		assertThat(feed.navigation()).extracting(l -> l.title()).containsExactly("All titles");
		assertThat(feed.groups()).hasSize(1);
		assertThat(feed.groups().get(0).metadata().title()).isEqualTo("New this term");
		assertThat(feed.groups().get(0).publications()).hasSize(1);
		assertThat(feed.groups().get(0).publications().get(0).metadata().title()).isEqualTo("Free Book");
	}

	@Test
	void rootFeedGroupReportsTheShelfsTrueEntitledCountNotJustThePreviewSize() {
		Institution institution = newInstitution("OPDS-ROOT-PREVIEW");
		Publisher publisher = newPublisher("OPDS-ROOT-PREVIEW-PUB");
		List<String> itemIds = new java.util.ArrayList<>();
		for (int i = 0; i < 11; i++) {
			itemIds.add(newItem(publisher.getId(), "Preview Book " + i, AccessTier.OPEN_ACCESS).getId());
		}
		saveFeedSettings(institution.getId(), new Shelf("shelf_1", "Big shelf", 1, itemIds));

		OpdsNavigationFeed feed = feedService.rootFeed(institution, subjectFor(institution));

		// 11 entitled books, but only the first 10 are embedded inline - numberOfItems must
		// still say 11, not 10, or the client thinks the shelf is smaller than it really is.
		assertThat(feed.groups().get(0).metadata().numberOfItems()).isEqualTo(11);
		assertThat(feed.groups().get(0).publications()).hasSize(10);
	}

	@Test
	void rootFeedMetadataNumberOfItemsIsTheWholeEntitledCatalogueNotJustCuratedShelves() {
		// "all" has no institution filter (open access is global), so the shared test database
		// makes an absolute count unreliable - assert the delta this test itself introduces
		// instead of a fixed expected total.
		Institution institution = newInstitution("OPDS-ROOT-COUNT");
		Publisher publisher = newPublisher("OPDS-ROOT-COUNT-PUB");
		int before = feedService.rootFeed(institution, subjectFor(institution)).metadata().numberOfItems();

		newItem(publisher.getId(), "Count Book 1", AccessTier.OPEN_ACCESS);
		newItem(publisher.getId(), "Count Book 2", AccessTier.OPEN_ACCESS);
		newItem(publisher.getId(), "Count Book 3", AccessTier.OPEN_ACCESS);

		int after = feedService.rootFeed(institution, subjectFor(institution)).metadata().numberOfItems();

		assertThat(after - before).isEqualTo(3);
	}

	@Test
	void rootFeedOmitsGroupsEntirelyWhenNoShelfHasBeenCurated() {
		Institution institution = newInstitution("OPDS-ROOT-NOSHELF");

		OpdsNavigationFeed feed = feedService.rootFeed(institution, subjectFor(institution));

		assertThat(feed.groups()).isNull();
		assertThat(feed.navigation()).extracting(l -> l.title()).containsExactly("All titles");
	}

	// ---------------------------------------------------------------------------- curated shelf

	@Test
	void curatedShelfFeedPreservesTheOperatorsExactOrder() {
		Institution institution = newInstitution("OPDS-SHELF-OK");
		Publisher publisher = newPublisher("OPDS-SHELF-PUB");
		CatalogueItem second = newItem(publisher.getId(), "Second Picked", AccessTier.OPEN_ACCESS);
		CatalogueItem first = newItem(publisher.getId(), "First Picked", AccessTier.OPEN_ACCESS);

		// Stored order is second-then-first, deliberately not publish order or alphabetical.
		Shelf shelf = new Shelf("shelf_1", "Staff picks", 1, List.of(second.getId(), first.getId()));
		saveFeedSettings(institution.getId(), shelf);

		OpdsPublicationFeed feed = feedService.groupFeed(institution, "shelf_1", subjectFor(institution),
				new PageQuery(0, 20), null, null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.containsExactly("Second Picked", "First Picked");
	}

	@Test
	void curatedShelfFeedIs404WhenEveryBookHasLostEntitlement() {
		Institution institution = newInstitution("OPDS-SHELF-EMPTY");
		Publisher publisher = newPublisher("OPDS-SHELF-EMPTY-PUB");
		CatalogueItem locked = newItem(publisher.getId(), "Locked Book", AccessTier.SUBSCRIPTION);

		Shelf shelf = new Shelf("shelf_1", "Dead shelf", 1, List.of(locked.getId()));
		saveFeedSettings(institution.getId(), shelf);

		assertThatThrownBy(() -> feedService.groupFeed(institution, "shelf_1", subjectFor(institution),
				new PageQuery(0, 20), null, null, null))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void groupFeedIs404ForAnUnrecognisedGroupId() {
		Institution institution = newInstitution("OPDS-BADGROUP");

		assertThatThrownBy(() -> feedService.groupFeed(institution, "shelf_9", subjectFor(institution),
				new PageQuery(0, 20), null, null, null))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	// ------------------------------------------------------------------------------- all group

	// The "all" query has no institution filter by design - every institution sees the same
	// catalogue, gated only by entitlement - so it runs against whatever else this shared,
	// never-reset test database holds (other tests' fixtures, the demo seed). Assertions here
	// check for presence/absence of this test's own items rather than the whole page.

	@Test
	void allGroupIncludesOpenAccessAndActivelyEntitledEliteBooksWithCopiesOnTheAcquisitionLink() {
		Institution institution = newInstitution("OPDS-ALL-OK");
		Publisher publisher = newPublisher("OPDS-ALL-PUB");
		CatalogueItem openAccessItem = newItem(publisher.getId(), "Open Book", AccessTier.OPEN_ACCESS);
		CatalogueItem eliteItem = newItem(publisher.getId(), "Elite Book", AccessTier.ELITE);
		CatalogueItem lockedItem = newItem(publisher.getId(), "Locked Book", AccessTier.SUBSCRIPTION);
		grantEliteEntitlement(institution.getId(), eliteItem.getId(), 3);

		OpdsPublicationFeed feed = feedService.groupFeed(institution, "all", subjectFor(institution),
				new PageQuery(0, 100), null, null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.contains("Open Book", "Elite Book")
				.doesNotContain("Locked Book");
		var eliteLink = feed.publications().stream()
				.filter(p -> p.metadata().title().equals("Elite Book"))
				.findFirst().orElseThrow()
				.links().stream()
				.filter(l -> l.properties() != null)
				.findFirst().orElseThrow();
		assertThat(eliteLink.properties().licenceModel()).isEqualTo(AccessTier.ELITE);
		assertThat(eliteLink.properties().copies().total()).isEqualTo(3);
		assertThat(eliteLink.properties().canPersist()).isFalse();
	}

	@Test
	void allGroupNeverFourOhFoursAndFallsBackToANavigationLinkWhenEmpty() {
		// SUBSCRIPTION always needs a per-institution grant, unlike OPEN_ACCESS - so a brand
		// new institution with zero entitlements sees none, regardless of what else exists in
		// this shared database. That is what makes an otherwise-impossible-to-arrange "zero
		// results" case reproducible here.
		Institution institution = newInstitution("OPDS-ALL-EMPTY");

		OpdsPublicationFeed feed = feedService.groupFeed(institution, "all", subjectFor(institution),
				new PageQuery(0, 20), null, null, AccessTier.SUBSCRIPTION);

		assertThat(feed.publications()).isNull();
		assertThat(feed.navigation()).hasSize(1);
		assertThat(feed.metadata().numberOfItems()).isZero();
	}

	@Test
	void allGroupNextLinkPreservesSortAndActiveFilters() {
		Institution institution = newInstitution("OPDS-ALL-NEXT");
		Publisher publisher = newPublisher("OPDS-ALL-NEXT-PUB");
		newItem(publisher.getId(), "Next Link Book A", AccessTier.OPEN_ACCESS);
		newItem(publisher.getId(), "Next Link Book B", AccessTier.OPEN_ACCESS);

		OpdsPublicationFeed feed = feedService.groupFeed(institution, "all", subjectFor(institution),
				new PageQuery(0, 1), "title.asc", ContentType.EPUB, AccessTier.OPEN_ACCESS);

		String next = feed.links().stream().filter(l -> "next".equals(l.rel())).findFirst()
				.orElseThrow(() -> new AssertionError("expected a next link since more than one page exists"))
				.href();
		assertThat(next).contains("page=1", "size=1", "sort=title.asc", "contentType=EPUB", "accessTier=OPEN_ACCESS");
	}

	// ----------------------------------------------------------------------------------- search

	@Test
	void searchFindsAnEntitledBookByTitleAndHidesAnUnentitledOne() {
		Institution institution = newInstitution("OPDS-SEARCH-TITLE");
		Publisher publisher = newPublisher("OPDS-SEARCH-TITLE-PUB");
		newItem(publisher.getId(), "Rights for Robots", AccessTier.OPEN_ACCESS);
		newItem(publisher.getId(), "Locked Robots Book", AccessTier.SUBSCRIPTION);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution), "robots",
				new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.containsExactly("Rights for Robots");
	}

	@Test
	void searchMatchesAHyphenatedIsbnAgainstTheStoredUnhyphenatedOne() {
		Institution institution = newInstitution("OPDS-SEARCH-ISBN");
		Publisher publisher = newPublisher("OPDS-SEARCH-ISBN-PUB");
		CatalogueItem item = newItem(publisher.getId(), "Rights for Robots", AccessTier.OPEN_ACCESS);
		item.setIsbn("9780367211745");
		catalogueItemRepository.save(item);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution),
				"978-0-367-21174-5", new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.containsExactly("Rights for Robots");
	}

	// A user-supplied regex metacharacter must never reach Mongo's $regex unescaped - a naive
	// pattern would either throw (unbalanced "(") or, worse, silently widen the match (unescaped
	// ".*" becomes a real wildcard). Confirms these are searched for literally.
	@Test
	void searchTreatsRegexMetacharactersAsLiteralTextNotAsAPattern() {
		Institution institution = newInstitution("OPDS-SEARCH-REGEX");
		Publisher publisher = newPublisher("OPDS-SEARCH-REGEX-PUB");
		newItem(publisher.getId(), "Robots (2020 edition)", AccessTier.OPEN_ACCESS);
		newItem(publisher.getId(), "Robots without parentheses", AccessTier.OPEN_ACCESS);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution),
				"Robots (2020 edition)", new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.containsExactly("Robots (2020 edition)");
	}

	@Test
	void searchWithOnlyRegexMetacharactersDoesNotThrowAndDoesNotMatchEverything() {
		Institution institution = newInstitution("OPDS-SEARCH-SYMBOLS");
		Publisher publisher = newPublisher("OPDS-SEARCH-SYMBOLS-PUB");
		newItem(publisher.getId(), "An Ordinary Title", AccessTier.OPEN_ACCESS);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution),
				"*.?[]{}", new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).isNull();
		assertThat(feed.navigation()).hasSize(1);
	}

	// Not SQL, so these are not an injection vector, but the search must still run to
	// completion and simply find nothing rather than erroring on the punctuation.
	@Test
	void searchWithSqlLikeInputFindsNothingRatherThanErroring() {
		Institution institution = newInstitution("OPDS-SEARCH-SQLI");

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution),
				"'; DROP TABLE users; --", new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).isNull();
		assertThat(feed.navigation()).hasSize(1);
	}

	@Test
	void searchWithAVeryLongQueryDoesNotThrow() {
		Institution institution = newInstitution("OPDS-SEARCH-LONG");
		String longQuery = "a".repeat(3000);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution), longQuery,
				new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).isNull();
		assertThat(feed.navigation()).hasSize(1);
	}

	@Test
	void searchIsCaseInsensitive() {
		Institution institution = newInstitution("OPDS-SEARCH-CASE");
		Publisher publisher = newPublisher("OPDS-SEARCH-CASE-PUB");
		newItem(publisher.getId(), "Ethereum Explained", AccessTier.OPEN_ACCESS);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution), "ETHEREUM",
				new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.containsExactly("Ethereum Explained");
	}

	// Open access has no institution filter (same as the "all" group - see the comment on
	// this shared, never-reset test database further up), so this uses a title unique to this
	// test rather than "Ethereum Explained", which searchIsCaseInsensitive also seeds and would
	// then double-count.
	@Test
	void searchMatchesAPrefixOrSubstringNotJustTheWholeWord() {
		Institution institution = newInstitution("OPDS-SEARCH-PREFIX");
		Publisher publisher = newPublisher("OPDS-SEARCH-PREFIX-PUB");
		newItem(publisher.getId(), "Zynthex Quantum Primer", AccessTier.OPEN_ACCESS);

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution), "zynth",
				new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.containsExactly("Zynthex Quantum Primer");
	}

	// The full battery from the edge-case review: every character class a search box can
	// receive, run against real Mongo. None of these may throw - a search endpoint that 500s
	// on a stray character or a pasted SQL/HTML/regex payload is worse than one that just
	// finds nothing. Metacharacter *escaping correctness* itself is InstitutionSearchRegexTest's
	// job (same escape() method, no Mongo needed there); this only proves the query completes.
	@ParameterizedTest
	@ValueSource(strings = {
			"ethereum blockchain", "12345", "abc123", "block-chain", "block_chain", "test.com", "foo/bar",
			"foo\\bar", "\"ethereum\"", "user's", "(ethereum)", "[ethereum]", "{ethereum}", "*", "eth*", "?",
			"%", "eth%", "_", "eth_", "' OR 1=1 --", "'; DROP TABLE users; --", "<script>alert(1)</script>",
			"é", "ñ", "中文", "தமிழ்", "🔥", "🚀", "ethereum\nblockchain", "ethereum\tblockchain",
			"aaaaaaaaaaaaaaaa", "!@#$%^&*()"
	})
	void searchNeverThrowsForAnyCharacterClass(String query) {
		Institution institution = newInstitution("OPDS-SEARCH-SWEEP-" + Math.abs(query.hashCode()));

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution), query,
				new PageQuery(0, 20), null, null);

		assertThat(feed).isNotNull();
	}

	@Test
	void searchWithNoMatchesReturnsANavigationLinkBackToTheCatalogueNotAnEmptyArray() {
		Institution institution = newInstitution("OPDS-SEARCH-EMPTY");

		OpdsPublicationFeed feed = feedService.searchFeed(institution, subjectFor(institution),
				"quantum knitting nonsense", new PageQuery(0, 20), null, null);

		assertThat(feed.publications()).isNull();
		assertThat(feed.navigation()).hasSize(1);
		assertThat(feed.metadata().numberOfItems()).isZero();
	}

	// --------------------------------------------------------------------------- publicationDocument

	@Test
	void publicationDocumentReturnsTheEntitledBookAsAStandaloneDocument() {
		Institution institution = newInstitution("OPDS-PUB-OK");
		Publisher publisher = newPublisher("OPDS-PUB-OK-PUB");
		CatalogueItem item = newItem(publisher.getId(), "Open Book", AccessTier.OPEN_ACCESS);

		OpdsPublicationDocument document = feedService.publicationDocument(institution, item.getId(),
				subjectFor(institution));

		assertThat(document.context()).isEqualTo("https://readium.org/webpub-manifest/context.jsonld");
		assertThat(document.metadata().title()).isEqualTo("Open Book");
	}

	@Test
	void publicationDocumentIs404WhenTheItemIsNotEntitled() {
		Institution institution = newInstitution("OPDS-PUB-LOCKED");
		Publisher publisher = newPublisher("OPDS-PUB-LOCKED-PUB");
		CatalogueItem item = newItem(publisher.getId(), "Locked Book", AccessTier.SUBSCRIPTION);

		assertThatThrownBy(() -> feedService.publicationDocument(institution, item.getId(), subjectFor(institution)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void publicationDocumentIs404ForAnUnknownItem() {
		Institution institution = newInstitution("OPDS-PUB-UNKNOWN");

		assertThatThrownBy(() -> feedService.publicationDocument(institution, "does-not-exist",
				subjectFor(institution)))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
