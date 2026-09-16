package com.tf.reader.catalogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemPublisherIndex;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.CatalogueItemRepository;
import com.tf.reader.catalogue.repository.ItemPublisherIndexRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.mongo.PublisherMongoClients;

/**
 * Proves the actual routing {@code CatalogueItemStore} does: opt-in per publisher, no competing
 * Spring beans (a second, locally-started {@code MongoDBContainer} stands in for a publisher's own
 * Mongo, connected to only via plain, unregistered {@code MongoTemplate} instances - see the
 * class-level Javadoc on {@link CatalogueItemStore} for why).
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class CatalogueItemStoreIT extends ContainerisedInfrastructure {

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

	private static final String PUBLISHER_SHARED = "pub_itemstore_shared";
	private static final String PUBLISHER_OWN_DB = "pub_itemstore_owndb";
	private static final String ITEM_SHARED = "item_store_shared";
	private static final String ITEM_OWN_DB = "item_store_owndb";

	@Autowired private CatalogueItemStore catalogueItemStore;
	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private ItemPublisherIndexRepository itemPublisherIndexRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private PublisherMongoClients publisherMongoClients;

	@AfterEach
	void removeWhatThisTestWrote() {
		catalogueItemRepository.deleteById(ITEM_SHARED);
		catalogueItemRepository.deleteById(ITEM_OWN_DB);
		itemPublisherIndexRepository.deleteById(ITEM_SHARED);
		itemPublisherIndexRepository.deleteById(ITEM_OWN_DB);
		publisherMongoClients.invalidate(PUBLISHER_OWN_DB);
		publisherRepository.deleteById(PUBLISHER_SHARED);
		publisherRepository.deleteById(PUBLISHER_OWN_DB);
	}

	@Test
	@DisplayName("a publisher with no Mongo of their own saves to and reads from the shared database")
	void routesToTheSharedDatabaseWhenPublisherHasNoOwnMongo() {
		publisher(PUBLISHER_SHARED, "ITEMSTORESHARED", null);

		catalogueItemStore.save(item(ITEM_SHARED, PUBLISHER_SHARED));

		assertThat(catalogueItemRepository.findById(ITEM_SHARED)).isPresent();
		assertThat(catalogueItemStore.findById(ITEM_SHARED)).isPresent();
	}

	@Test
	@DisplayName("a publisher with their own Mongo saves to and reads from it instead")
	void routesToThePublishersOwnDatabaseWhenConfigured() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_itemstore_owndb_test";
		publisher(PUBLISHER_OWN_DB, "ITEMSTOREOWNDB", ownMongoUri);

		catalogueItemStore.save(item(ITEM_OWN_DB, PUBLISHER_OWN_DB));

		// Not in the shared database at all.
		assertThat(catalogueItemRepository.findById(ITEM_OWN_DB)).isEmpty();

		// Genuinely present in the publisher's own database, queried directly.
		MongoTemplate ownTemplate = new MongoTemplate(publisherMongoClients.factoryFor(PUBLISHER_OWN_DB, ownMongoUri));
		Document raw = ownTemplate.getCollection("catalogueItems").find(new Document("_id", ITEM_OWN_DB)).first();
		assertThat(raw).isNotNull();

		// The store finds it there too, via the shared index.
		Optional<CatalogueItem> found = catalogueItemStore.findById(ITEM_OWN_DB);
		assertThat(found).isPresent();
		assertThat(found.get().getPublisherId()).isEqualTo(PUBLISHER_OWN_DB);
	}

	@Test
	@DisplayName("a save to a publisher's own database keeps the shared item-publisher index up to date")
	void keepsTheSharedIndexInSyncForAnOwnDatabaseSave() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_itemstore_owndb_test2";
		publisher(PUBLISHER_OWN_DB, "ITEMSTOREOWNDB", ownMongoUri);

		catalogueItemStore.save(item(ITEM_OWN_DB, PUBLISHER_OWN_DB));

		ItemPublisherIndex indexed = itemPublisherIndexRepository.findById(ITEM_OWN_DB).orElseThrow();
		assertThat(indexed.getPublisherId()).isEqualTo(PUBLISHER_OWN_DB);
	}

	@Test
	@DisplayName("findByContentState fans out across the shared database and every publisher's own database")
	void findByContentStateFansOutAcrossEveryDatabase() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_itemstore_owndb_test3";
		publisher(PUBLISHER_SHARED, "ITEMSTORESHARED", null);
		publisher(PUBLISHER_OWN_DB, "ITEMSTOREOWNDB", ownMongoUri);

		CatalogueItem sharedItem = item(ITEM_SHARED, PUBLISHER_SHARED);
		sharedItem.setContentState(ContentState.QUEUED);
		catalogueItemStore.save(sharedItem);

		CatalogueItem ownDbItem = item(ITEM_OWN_DB, PUBLISHER_OWN_DB);
		ownDbItem.setContentState(ContentState.QUEUED);
		catalogueItemStore.save(ownDbItem);

		List<String> foundIds = catalogueItemStore.findByContentState(ContentState.QUEUED).stream()
				.map(CatalogueItem::getId).toList();

		assertThat(foundIds).contains(ITEM_SHARED, ITEM_OWN_DB);
	}

	@Test
	@DisplayName("findByContentStateInAndUpdatedAtBefore fans out the same way, for the watchdog")
	void findByContentStateInAndUpdatedAtBeforeFansOutAcrossEveryDatabase() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_itemstore_owndb_test4";
		publisher(PUBLISHER_SHARED, "ITEMSTORESHARED", null);
		publisher(PUBLISHER_OWN_DB, "ITEMSTOREOWNDB", ownMongoUri);

		CatalogueItem sharedItem = item(ITEM_SHARED, PUBLISHER_SHARED);
		sharedItem.setContentState(ContentState.PROCESSING);
		sharedItem.setUpdatedAt(Instant.now().minusSeconds(120));
		catalogueItemStore.save(sharedItem);

		CatalogueItem ownDbItem = item(ITEM_OWN_DB, PUBLISHER_OWN_DB);
		ownDbItem.setContentState(ContentState.PROCESSING);
		ownDbItem.setUpdatedAt(Instant.now().minusSeconds(120));
		catalogueItemStore.save(ownDbItem);

		List<String> foundIds = catalogueItemStore
				.findByContentStateInAndUpdatedAtBefore(List.of(ContentState.QUEUED, ContentState.PROCESSING),
						Instant.now().plusSeconds(60))
				.stream().map(CatalogueItem::getId).toList();

		assertThat(foundIds).contains(ITEM_SHARED, ITEM_OWN_DB);
	}

	@Test
	@DisplayName("saving an item for a publisher that does not exist is rejected, same as the shared-database guard")
	void rejectsASaveForAnUnknownPublisher() {
		assertThatThrownBy(() -> catalogueItemStore.save(item(ITEM_SHARED, "pub_does_not_exist")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not reference an existing publisher");
	}

	private void publisher(String id, String code, String mongoUri) {
		Publisher publisher = new Publisher(id, code, code, null, null, RecordStatus.ACTIVE, null, null);
		publisher.setMongoUri(mongoUri);
		publisherRepository.save(publisher);
	}

	private static CatalogueItem item(String id, String publisherId) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setPublisherId(publisherId);
		item.setTitle("Catalogue Item Store Fixture");
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.SUBSCRIPTION);
		item.setStatus(ItemStatus.DRAFT);
		item.setContentState(ContentState.NONE);
		return item;
	}

}
