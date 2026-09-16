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
import com.tf.reader.catalogue.repository.CatalogueItemPublicSearchRepository;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.mongo.PublisherMongoClients;

/**
 * Previously had no test coverage at all - this pins the anonymous public catalogue search's
 * always-PUBLISHED/READY, always-cross-publisher behaviour, and the fan-out this plan adds.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CatalogueItemPublicSearchRepositoryTest {

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

	@Autowired private CatalogueItemPublicSearchRepository publicSearchRepository;
	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private PublisherMongoClients publisherMongoClients;

	private String publisherId;

	@BeforeEach
	void seed() {
		catalogueItemRepository.deleteAll();
		publisherRepository.deleteAll();

		publisherId = publisherRepository
				.save(new Publisher(null, "PUB-PUBSEARCH", "Public Search Press", null, null, RecordStatus.ACTIVE,
						Instant.now(), Instant.now()))
				.getId();

		catalogueItemRepository.save(item("item_pub_1", publisherId, "Robotics for Beginners", ItemStatus.PUBLISHED,
				ContentState.READY));
		catalogueItemRepository.save(item("item_pub_2", publisherId, "Draft Only", ItemStatus.DRAFT,
				ContentState.NONE));
		catalogueItemRepository.save(item("item_pub_3", publisherId, "Not Ready Yet", ItemStatus.PUBLISHED,
				ContentState.PROCESSING));
	}

	private static CatalogueItem item(String id, String publisherId, String title, ItemStatus status,
			ContentState contentState) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setPublisherId(publisherId);
		item.setTitle(title);
		item.setAccessTier(AccessTier.OPEN_ACCESS);
		item.setContentType(ContentType.PDF);
		item.setStatus(status);
		item.setContentState(contentState);
		item.setCreatedAt(Instant.now());
		item.setUpdatedAt(Instant.now());
		return item;
	}

	@Test
	void onlyPublishedAndReadyItemsMatch() {
		var results = publicSearchRepository.search("robotics", null, null, 0, 20);

		assertThat(results.items()).extracting(CatalogueItem::getId).containsExactly("item_pub_1");
		assertThat(results.total()).isEqualTo(1);
	}

	@Test
	void fansOutToAPublishersOwnDatabase() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_pubsearch_owndb_test";
		String ownDbPublisherId = publisherRepository
				.save(new Publisher(null, "OWNDB-PUBSEARCH", "Own Database Press", null, null, RecordStatus.ACTIVE,
						Instant.now(), Instant.now()))
				.getId();
		publisherRepository.findById(ownDbPublisherId).ifPresent(p -> {
			p.setMongoUri(ownMongoUri);
			publisherRepository.save(p);
		});

		CatalogueItem ownDbItem = item("item_pub_owndb", ownDbPublisherId, "Aardvark Robotics", ItemStatus.PUBLISHED,
				ContentState.READY);
		new MongoTemplate(publisherMongoClients.factoryFor(ownDbPublisherId, ownMongoUri)).save(ownDbItem);

		try {
			var results = publicSearchRepository.search("robotics", null, null, 0, 20);

			assertThat(results.items()).extracting(CatalogueItem::getId).containsExactlyInAnyOrder("item_pub_1",
					"item_pub_owndb");
			assertThat(results.total()).isEqualTo(2);
			// Title-ascending: "Aardvark Robotics" sorts before "Robotics for Beginners".
			assertThat(results.items().get(0).getId()).isEqualTo("item_pub_owndb");
		} finally {
			publisherMongoClients.invalidate(ownDbPublisherId);
			publisherRepository.deleteById(ownDbPublisherId);
		}
	}

	@Test
	void paginatesOverTheMergedResults() {
		var page0 = publicSearchRepository.search("robotics", null, null, 0, 1);
		assertThat(page0.items()).hasSize(1);
		assertThat(page0.total()).isEqualTo(1);
	}

}
