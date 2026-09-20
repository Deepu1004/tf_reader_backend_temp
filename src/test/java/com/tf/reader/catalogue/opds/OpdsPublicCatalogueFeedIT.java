package com.tf.reader.catalogue.opds;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.tf.reader.catalogue.opds.dto.OpdsPublicationFeed;
import com.tf.reader.catalogue.opds.service.OpdsPublicFeedService;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;

/**
 * The no-sign-in home feed a signed-out reader actually browses (Workstream 9's other half of
 * {@link OpdsPublicFeedServiceIT}, which covers the publication-detail endpoint only):
 * {@link OpdsPublicFeedService#catalogueFeed} - open access titles a signed-out reader can
 * actually open, covers included - and {@link OpdsPublicFeedService#journalsFeed}, its
 * institution-free counterpart to {@code OpdsFeedService.rootFeed}'s "Journals" group.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class OpdsPublicCatalogueFeedIT extends ContainerisedInfrastructure {

	@Autowired private OpdsPublicFeedService publicFeedService;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;

	private Publisher newPublisher(String code) {
		return publisherRepository.save(
				new Publisher(null, code, code + " Press", null, null, RecordStatus.ACTIVE, null, null));
	}

	private CatalogueItem newLeaf(String publisherId, WorkType workType, String title) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setCollectionIds(List.of());
		item.setWorkType(workType);
		item.setTitle(title);
		item.setAccessTier(AccessTier.OPEN_ACCESS);
		item.setStatus(ItemStatus.PUBLISHED);
		item.setContentState(ContentState.READY);
		item.setContentType(ContentType.EPUB);
		item.setPublishedAt(LocalDate.of(2026, 1, 1));
		item.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(item);
	}

	private CatalogueItem newJournal(String publisherId, String title) {
		CatalogueItem journal = new CatalogueItem();
		journal.setPublisherId(publisherId);
		journal.setWorkType(WorkType.JOURNAL);
		journal.setTitle(title);
		journal.setStatus(ItemStatus.PUBLISHED);
		journal.setUpdatedAt(Instant.parse("2026-08-10T09:00:00Z"));
		return catalogueItemRepository.save(journal);
	}

	// The actual bug: a bare journal ARTICLE has no cover of its own (only the JOURNAL container
	// does), so mixing articles into this feed left a signed-out reader looking at a grid that was
	// mostly cover-less - see catalogueFeed()'s own comment.
	@Test
	void catalogueFeedExcludesBareArticlesButKeepsBooks() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-FEED-FILTER-PUB");
		CatalogueItem book = newLeaf(publisher.getId(), WorkType.BOOK, "An Open Access Book");
		newLeaf(publisher.getId(), WorkType.ARTICLE, "A Bare Journal Article");

		OpdsPublicationFeed feed = publicFeedService.catalogueFeed(new PageQuery(0, 20));

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.contains("An Open Access Book")
				.doesNotContain("A Bare Journal Article");
		assertThat(feed.publications()).extracting(p -> p.links().get(0).href())
				.anyMatch(href -> href.contains(book.getId()));
	}

	// Two legacy dev fixtures predate the workType field and carry no value at all - this pins
	// that they are NOT swept up by the article exclusion above, matching how every other reader
	// of this field in the codebase (IngestService, CatalogueItemAdminService) treats an absent
	// workType as BOOK, not as "unknown, so exclude it."
	@Test
	void catalogueFeedKeepsAnItemWithNoWorkTypeSetAtAll() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-FEED-NO-WORKTYPE-PUB");
		newLeaf(publisher.getId(), null, "Predates The workType Field");

		OpdsPublicationFeed feed = publicFeedService.catalogueFeed(new PageQuery(0, 20));

		assertThat(feed.publications()).extracting(p -> p.metadata().title())
				.contains("Predates The workType Field");
	}

	@Test
	void journalsFeedListsEveryPublishedJournalWithItsCover() {
		Publisher publisher = newPublisher("OPDS-PUBLIC-JOURNALS-PUB");
		CatalogueItem journal = newJournal(publisher.getId(), "Journal Of Public Browsing");
		journal.setCoverKey("items/" + journal.getId() + "/cover");
		journal.setCoverMimeType("image/jpeg");
		catalogueItemRepository.save(journal);

		try {
			OpdsPublicationFeed feed = publicFeedService.journalsFeed();

			var publication = feed.publications().stream()
					.filter(p -> p.metadata().title().equals("Journal Of Public Browsing")).findFirst()
					.orElseThrow(() -> new AssertionError("journal missing from " + feed.publications()));
			assertThat(publication.images()).isNotNull().hasSize(1);
			assertThat(publication.images().get(0).href()).contains(journal.getCoverKey());
			assertThat(publication.links()).singleElement().satisfies(link -> {
				assertThat(link.rel()).isEqualTo("subsection");
				assertThat(link.href()).contains(journal.getId());
			});
		} finally {
			// journalsFeed()'s query is GLOBAL (every PUBLISHED journal, no publisher/scope filter),
			// and ContainerisedInfrastructure's Mongo is shared, un-truncated, across the whole
			// suite for the JVM's life - left behind, this journal is exactly the kind of stray
			// fixture that broke OpdsFeedServiceIT's own rootFeed() assertions the first time this
			// test ran without this cleanup.
			catalogueItemRepository.delete(journal);
		}
	}

	// No "empty catalogue" test for journalsFeed(), deliberately, matching
	// OpdsFeedServiceIT's own rootFeed() Journals-group tests: this query is global (every
	// PUBLISHED journal, no publisher/scope filter - same as rootFeed()'s), and
	// ContainerisedInfrastructure's Mongo container is shared, un-truncated, across the WHOLE
	// suite for the life of the JVM. "Zero journals exist anywhere" is not a state any single
	// test can establish once other test classes are free to leave their own journals behind.
}
