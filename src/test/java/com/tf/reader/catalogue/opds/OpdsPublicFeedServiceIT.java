package com.tf.reader.catalogue.opds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.WorkType;
import com.tf.reader.catalogue.opds.dto.OpdsNavigationFeed;
import com.tf.reader.catalogue.opds.dto.OpdsPublication;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationDocument;
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;

/**
 * The no-sign-in publication detail endpoint (Workstream 9): metadata for any
 * {@code PUBLISHED}/{@code READY} book regardless of entitlement, and a {@code 404} for
 * everything else - unknown, archived, still a draft, or published but not yet processed.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class OpdsPublicFeedServiceIT extends ContainerisedInfrastructure {

	@Autowired private OpdsPublicFeedService publicFeedService;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;

	private Publisher newPublisher(String code) {
		return publisherRepository.save(
				new Publisher(null, code, code + " Press", null, null, RecordStatus.ACTIVE, null, null));
	}

	private CatalogueItem newItem(String publisherId, AccessTier accessTier, ItemStatus status,
			ContentState contentState) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setCollectionIds(List.of());
		item.setTitle("Publication Detail Fixture");
		// No isbn: none of this class's tests assert on it, and every test in this class calls
		// newItem() - a shared literal here collided with the unique isbn index across this
		// class's own tests (and with OpdsFeedServiceIT's own single, unrelated use of the same
		// literal) the moment failsafe started actually running the full IT suite.
		item.setAccessTier(accessTier);
		item.setStatus(status);
		item.setContentState(contentState);
		item.setContentType(ContentType.EPUB);
		item.setPublishedAt(LocalDate.of(2026, 1, 1));
		item.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(item);
	}

	private CatalogueItem newContainer(String publisherId, WorkType workType, String parentId, String title) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setWorkType(workType);
		item.setParentId(parentId);
		item.setTitle(title);
		item.setStatus(ItemStatus.PUBLISHED);
		item.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(item);
	}

	// ---------------------------------------------------------------------------- catalogueFeed

	@Test
	void catalogueFeedExcludesArticlesVolumesAndIssuesButIncludesTheJournal() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-JOURNAL-PUB");
		CatalogueItem journal = newContainer(publisher.getId(), WorkType.JOURNAL, null, "Public Feed Journal");
		newContainer(publisher.getId(), WorkType.VOLUME, journal.getId(), "Public Feed Volume");
		CatalogueItem issue = newContainer(publisher.getId(), WorkType.ISSUE, journal.getId(), "Public Feed Issue");
		CatalogueItem article = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.READY);
		article.setWorkType(WorkType.ARTICLE);
		article.setParentId(issue.getId());
		article.setTitle("Public Feed Article");
		catalogueItemRepository.save(article);

		OpdsPublicationFeed feed = publicFeedService.catalogueFeed(new PageQuery(0, 500));

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.contains("Public Feed Journal")
				.doesNotContain("Public Feed Volume", "Public Feed Issue", "Public Feed Article");
	}

	@Test
	void catalogueFeedMapsAJournalAsAContainerPublicationWithASubsectionLinkAndCover() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-JOURNAL-COVER-PUB");
		CatalogueItem journal = newContainer(publisher.getId(), WorkType.JOURNAL, null,
				"Public Feed Journal With Cover");
		journal.setCoverKey("items/" + journal.getId() + "/cover");
		journal.setCoverMimeType("image/jpeg");
		catalogueItemRepository.save(journal);

		OpdsPublicationFeed feed = publicFeedService.catalogueFeed(new PageQuery(0, 500));

		OpdsPublication publication = feed.publications().stream()
				.filter(p -> p.metadata().title().equals("Public Feed Journal With Cover")).findFirst()
				.orElseThrow();
		assertThat(publication.images()).isNotNull().hasSize(1);
		assertThat(publication.images().get(0).href()).contains(journal.getCoverKey());
		assertThat(publication.links()).singleElement().satisfies(link -> {
			assertThat(link.rel()).isEqualTo("subsection");
			assertThat(link.href()).contains(journal.getId());
		});
	}

	@Test
	void catalogueFeedStillIncludesALegacyBookWithNullWorkType() {
		// workType is null for every pre-hierarchy book, not literally BOOK - the exclusion
		// filter (NotIn ARTICLE/VOLUME/ISSUE) must not accidentally drop these.
		Publisher publisher = newPublisher("OPDS-PUBLIC-LEGACY-PUB");
		CatalogueItem legacyBook = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.READY);
		legacyBook.setWorkType(null);
		legacyBook.setTitle("Legacy Book With No WorkType");
		catalogueItemRepository.save(legacyBook);

		OpdsPublicationFeed feed = publicFeedService.catalogueFeed(new PageQuery(0, 500));

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.contains("Legacy Book With No WorkType");
	}

	// -------------------------------------------------------------------------------- workFeed

	@Test
	void workFeedOnAJournalReturnsNavigationToItsVolumes() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-WORK-JOURNAL-PUB");
		CatalogueItem journal = newContainer(publisher.getId(), WorkType.JOURNAL, null, "Public Work Journal");
		newContainer(publisher.getId(), WorkType.VOLUME, journal.getId(), "Public Work Volume 1");

		Object feed = publicFeedService.workFeed(journal.getId());

		assertThat(feed).isInstanceOf(OpdsNavigationFeed.class);
		OpdsNavigationFeed navFeed = (OpdsNavigationFeed) feed;
		assertThat(navFeed.metadata().title()).isEqualTo("Public Work Journal");
		assertThat(navFeed.navigation()).hasSize(1);
		assertThat(navFeed.navigation().get(0).title()).isEqualTo("Public Work Volume 1");
	}

	@Test
	void workFeedOnAnIssueReturnsOnlyItsOpenAccessArticles() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-WORK-ISSUE-PUB");
		CatalogueItem issue = newContainer(publisher.getId(), WorkType.ISSUE, null, "Public Work Issue");
		CatalogueItem openArticle = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.READY);
		openArticle.setWorkType(WorkType.ARTICLE);
		openArticle.setParentId(issue.getId());
		openArticle.setTitle("Open Article");
		catalogueItemRepository.save(openArticle);
		// No entitlement check exists on this path - a SUBSCRIPTION sibling must be excluded
		// outright rather than carried with a subscribe link, unlike the authenticated workFeed.
		CatalogueItem subscriptionArticle = newItem(publisher.getId(), AccessTier.SUBSCRIPTION, ItemStatus.PUBLISHED,
				ContentState.READY);
		subscriptionArticle.setWorkType(WorkType.ARTICLE);
		subscriptionArticle.setParentId(issue.getId());
		subscriptionArticle.setTitle("Subscription Article");
		catalogueItemRepository.save(subscriptionArticle);

		Object feed = publicFeedService.workFeed(issue.getId());

		assertThat(feed).isInstanceOf(OpdsPublicationFeed.class);
		OpdsPublicationFeed pubFeed = (OpdsPublicationFeed) feed;
		assertThat(pubFeed.publications()).extracting(p -> p.metadata().title()).containsExactly("Open Article");
	}

	@Test
	void workFeedOnAnUnpublishedOrUnknownWorkIs404() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-WORK-404-PUB");
		CatalogueItem journal = newContainer(publisher.getId(), WorkType.JOURNAL, null, "Draft Public Journal");
		journal.setStatus(ItemStatus.DRAFT);
		catalogueItemRepository.save(journal);

		assertThatThrownBy(() -> publicFeedService.workFeed(journal.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));

		assertThatThrownBy(() -> publicFeedService.workFeed("does-not-exist"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	// --------------------------------------------------------------------- publicationDocument

	@Test
	void openAccessItemCarriesTheRealAcquisitionLink() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-OA-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.READY);

		OpdsPublicationDocument document = publicFeedService.publicationDocument(item.getId());

		assertThat(document.context()).isEqualTo("https://readium.org/webpub-manifest/context.jsonld");
		assertThat(document.links()).extracting("rel")
				.contains("http://opds-spec.org/acquisition/open-access");
	}

	@Test
	void eliteItemCarriesASubscribeLinkMarkedUnavailable() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-ELITE-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.ELITE, ItemStatus.PUBLISHED, ContentState.READY);

		OpdsPublicationDocument document = publicFeedService.publicationDocument(item.getId());

		assertThat(document.links()).extracting("rel")
				.contains("http://opds-spec.org/acquisition/subscribe");
	}

	@Test
	void unknownItemIsNotFound() {
		assertThatThrownBy(() -> publicFeedService.publicationDocument("item_does_not_exist"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void archivedItemIsNotFound() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-ARCHIVED-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.ARCHIVED,
				ContentState.READY);

		assertThatThrownBy(() -> publicFeedService.publicationDocument(item.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void draftItemIsNotFound() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-DRAFT-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.DRAFT,
				ContentState.READY);

		assertThatThrownBy(() -> publicFeedService.publicationDocument(item.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void publishedButNotYetReadyItemIsNotFound() {
		Publisher publisher = newPublisher("OPDS-DOC-IT-QUEUED-PUB");
		CatalogueItem item = newItem(publisher.getId(), AccessTier.OPEN_ACCESS, ItemStatus.PUBLISHED,
				ContentState.QUEUED);

		assertThatThrownBy(() -> publicFeedService.publicationDocument(item.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).code()).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
