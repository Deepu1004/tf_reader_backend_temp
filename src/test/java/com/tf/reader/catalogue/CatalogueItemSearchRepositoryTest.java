package com.tf.reader.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.tf.reader.TestcontainersConfiguration;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.CatalogueItemSearchRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.mongo.PublisherMongoClients;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CatalogueItemSearchRepositoryTest {

	// Stands in for "a publisher's own MongoDB," independent of the shared container above.
	@SuppressWarnings("resource")
	private static final MongoDBContainer OWN_MONGO = new MongoDBContainer(DockerImageName.parse("mongo:latest"));

	@BeforeAll
	static void startOwnMongo() {
		OWN_MONGO.start();
	}

	@AfterAll
	static void stopOwnMongo() {
		OWN_MONGO.stop();
	}

	@Autowired
	private CatalogueItemSearchRepository searchRepository;
	@Autowired
	private CatalogueItemRepository catalogueItemRepository;
	@Autowired
	private PublisherRepository publisherRepository;
	@Autowired
	private PublisherMongoClients publisherMongoClients;

	private String routledgeId;
	private String crcId;

	@BeforeEach
	void seed() {
		catalogueItemRepository.deleteAll();
		publisherRepository.deleteAll();

		routledgeId = publisherRepository
				.save(new Publisher(null, "RTLG-CISEARCH", "Routledge", null, null, RecordStatus.ACTIVE, Instant.now(),
						Instant.now()))
				.getId();
		crcId = publisherRepository
				.save(new Publisher(null, "CRC-CISEARCH", "CRC Press", null, null, RecordStatus.ACTIVE, Instant.now(),
						Instant.now()))
				.getId();

		save(item("item_ci_1", routledgeId, List.of("col_ci_law"), "Rights for Robots",
				List.of("Joshua C. Gellers"), List.of("Law", "Technology"), "9780367211745", ContentType.PDF,
				AccessTier.ELITE, ContentState.QUEUED));
		save(item("item_ci_2", routledgeId, List.of("col_ci_law"), "Environmental Law Basics", List.of("A. Author"),
				List.of("Environment"), null, ContentType.EPUB, AccessTier.SUBSCRIPTION, ContentState.READY));
		save(item("item_ci_3", crcId, List.of(), "Concrete Structures", List.of("B. Builder"), List.of("Engineering"),
				null, ContentType.PDF, AccessTier.OPEN_ACCESS, ContentState.FAILED));
	}

	private void save(CatalogueItem item) {
		catalogueItemRepository.save(item);
	}

	private static CatalogueItem item(String id, String publisherId, List<String> collectionIds, String title,
			List<String> authors, List<String> subjects, String isbn, ContentType contentType, AccessTier accessTier,
			ContentState contentState) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setPublisherId(publisherId);
		item.setCollectionIds(collectionIds);
		item.setTitle(title);
		item.setAuthors(authors);
		item.setSubjects(subjects);
		item.setIsbn(isbn);
		item.setContentType(contentType);
		item.setAccessTier(accessTier);
		item.setStatus(ItemStatus.PUBLISHED);
		item.setContentState(contentState);
		item.setCreatedAt(Instant.now());
		item.setUpdatedAt(Instant.now());
		return item;
	}

	@Test
	void filtersByPublisherId() {
		var results = searchRepository.search(routledgeId, null, null, null, null, 0, 20);
		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactlyInAnyOrder("item_ci_1",
				"item_ci_2");
	}

	@Test
	void filtersByCollectionId() {
		var results = searchRepository.search(null, "col_ci_law", null, null, null, 0, 20);
		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactlyInAnyOrder("item_ci_1",
				"item_ci_2");
	}

	@Test
	void filtersByContentTypeAndAccessTier() {
		var results = searchRepository.search(null, null, ContentType.PDF, AccessTier.ELITE, null, 0, 20);
		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_1");
	}

	@Test
	void combinesFiltersFreely() {
		var results = searchRepository.search(routledgeId, "col_ci_law", ContentType.EPUB, AccessTier.SUBSCRIPTION,
				null, 0, 20);
		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_2");
	}

	@Test
	void isbnShapedQMatchesExactly() {
		var results = searchRepository.search(null, null, null, null, "978-0-367-21174-5", 0, 20);
		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_1");
	}

	@Test
	void titleShapedQMatchesSubstringAcrossTitleAuthorsAndSubjects() {
		var byTitle = searchRepository.search(null, null, null, null, "robots", 0, 20);
		assertThat(byTitle.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_1");

		var byAuthor = searchRepository.search(null, null, null, null, "Builder", 0, 20);
		assertThat(byAuthor.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_3");

		var bySubject = searchRepository.search(null, null, null, null, "Engineering", 0, 20);
		assertThat(bySubject.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_3");
	}

	@Test
	void queuedAndFailedItemsStillAppearWithNoContentStateFilter() {
		var results = searchRepository.search(null, null, null, null, null, 0, 20);
		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactlyInAnyOrder("item_ci_1",
				"item_ci_2", "item_ci_3");
		assertThat(results.total()).isEqualTo(3);
	}

	@Test
	void unscopedSearchFansOutToAPublishersOwnDatabaseAndMergesTheTotal() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_cisearch_owndb_test";
		String ownDbPublisherId = publisherRepository
				.save(new Publisher(null, "OWNDB-CISEARCH", "Own Database Press", null, null, RecordStatus.ACTIVE,
						Instant.now(), Instant.now()))
				.getId();
		publisherRepository.findById(ownDbPublisherId).ifPresent(p -> {
			p.setMongoUri(ownMongoUri);
			publisherRepository.save(p);
		});

		CatalogueItem ownDbItem = item("item_ci_owndb", ownDbPublisherId, List.of(), "Aardvark Anatomy",
				List.of("Z. Zoologist"), List.of("Biology"), null, ContentType.PDF, AccessTier.OPEN_ACCESS,
				ContentState.READY);
		new MongoTemplate(publisherMongoClients.factoryFor(ownDbPublisherId, ownMongoUri)).save(ownDbItem);

		try {
			var results = searchRepository.search(null, null, null, null, null, 0, 20);

			// The three shared-database fixtures plus the one in the publisher's own database.
			assertThat(results.items()).extracting(CatalogueItem::getId).containsExactlyInAnyOrder("item_ci_1",
					"item_ci_2", "item_ci_3", "item_ci_owndb");
			assertThat(results.total()).isEqualTo(4);
			// Title-ascending: "Aardvark Anatomy" sorts before every shared-database fixture title.
			assertThat(results.items().get(0).getId()).isEqualTo("item_ci_owndb");
		} finally {
			publisherMongoClients.invalidate(ownDbPublisherId);
			publisherRepository.deleteById(ownDbPublisherId);
		}
	}

	@Test
	void publisherScopedSearchRoutesToThatPublishersOwnDatabaseOnly() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_cisearch_owndb_test2";
		String ownDbPublisherId = publisherRepository
				.save(new Publisher(null, "OWNDB2-CISEARCH", "Own Database Press Two", null, null,
						RecordStatus.ACTIVE, Instant.now(), Instant.now()))
				.getId();
		publisherRepository.findById(ownDbPublisherId).ifPresent(p -> {
			p.setMongoUri(ownMongoUri);
			publisherRepository.save(p);
		});

		CatalogueItem ownDbItem = item("item_ci_owndb2", ownDbPublisherId, List.of(), "Beekeeping Basics",
				List.of(), List.of(), null, ContentType.PDF, AccessTier.OPEN_ACCESS, ContentState.READY);
		new MongoTemplate(publisherMongoClients.factoryFor(ownDbPublisherId, ownMongoUri)).save(ownDbItem);

		try {
			var results = searchRepository.search(ownDbPublisherId, null, null, null, null, 0, 20);

			assertThat(results.items()).extracting(CatalogueItem::getId).containsExactly("item_ci_owndb2");
			assertThat(results.total()).isEqualTo(1);
		} finally {
			publisherMongoClients.invalidate(ownDbPublisherId);
			publisherRepository.deleteById(ownDbPublisherId);
		}
	}

}
