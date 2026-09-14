package com.tf.reader.catalogue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.CollectionPublisherIndex;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.BookCollectionRepository;
import com.tf.reader.catalogue.repository.CollectionPublisherIndexRepository;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.mongo.PublisherMongoClients;

/**
 * {@link CatalogueItemStoreIT}'s counterpart for {@link BookCollectionStore}: proves the same
 * routing works for collections, including the collection-only fan-out ({@link BookCollectionStore#findAll}).
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class BookCollectionStoreIT extends ContainerisedInfrastructure {

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

	private static final String PUBLISHER_SHARED = "pub_collectionstore_shared";
	private static final String PUBLISHER_OWN_DB = "pub_collectionstore_owndb";
	private static final String COLLECTION_SHARED = "col_store_shared";
	private static final String COLLECTION_OWN_DB = "col_store_owndb";

	@Autowired private BookCollectionStore bookCollectionStore;
	@Autowired private BookCollectionRepository bookCollectionRepository;
	@Autowired private CollectionPublisherIndexRepository collectionPublisherIndexRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private PublisherMongoClients publisherMongoClients;

	@AfterEach
	void removeWhatThisTestWrote() {
		bookCollectionRepository.deleteById(COLLECTION_SHARED);
		bookCollectionRepository.deleteById(COLLECTION_OWN_DB);
		collectionPublisherIndexRepository.deleteById(COLLECTION_SHARED);
		collectionPublisherIndexRepository.deleteById(COLLECTION_OWN_DB);
		publisherMongoClients.invalidate(PUBLISHER_OWN_DB);
		publisherRepository.deleteById(PUBLISHER_SHARED);
		publisherRepository.deleteById(PUBLISHER_OWN_DB);
	}

	@Test
	@DisplayName("a publisher with no Mongo of their own saves to and reads from the shared database")
	void routesToTheSharedDatabaseWhenPublisherHasNoOwnMongo() {
		publisher(PUBLISHER_SHARED, "COLSTORESHARED", null);

		bookCollectionStore.save(collection(COLLECTION_SHARED, PUBLISHER_SHARED, "SHARED2024"));

		assertThat(bookCollectionRepository.findById(COLLECTION_SHARED)).isPresent();
		assertThat(bookCollectionStore.findById(COLLECTION_SHARED)).isPresent();
	}

	@Test
	@DisplayName("a publisher with their own Mongo saves to and reads from it instead")
	void routesToThePublishersOwnDatabaseWhenConfigured() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_collectionstore_owndb_test";
		publisher(PUBLISHER_OWN_DB, "COLSTOREOWNDB", ownMongoUri);

		bookCollectionStore.save(collection(COLLECTION_OWN_DB, PUBLISHER_OWN_DB, "OWNDB2024"));

		assertThat(bookCollectionRepository.findById(COLLECTION_OWN_DB)).isEmpty();

		MongoTemplate ownTemplate = new MongoTemplate(publisherMongoClients.factoryFor(PUBLISHER_OWN_DB, ownMongoUri));
		assertThat(ownTemplate.findById(COLLECTION_OWN_DB, BookCollection.class)).isNotNull();

		Optional<BookCollection> found = bookCollectionStore.findById(COLLECTION_OWN_DB);
		assertThat(found).isPresent();
		assertThat(found.get().getPublisherId()).isEqualTo(PUBLISHER_OWN_DB);

		CollectionPublisherIndex indexed = collectionPublisherIndexRepository.findById(COLLECTION_OWN_DB).orElseThrow();
		assertThat(indexed.getPublisherId()).isEqualTo(PUBLISHER_OWN_DB);
	}

	@Test
	@DisplayName("findByPublisherId, findByPublisherIdAndCode and countByPublisherId all route to a known publisher's own database")
	void scopedQueriesRouteToTheKnownPublishersDatabase() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_collectionstore_owndb_test2";
		publisher(PUBLISHER_OWN_DB, "COLSTOREOWNDB", ownMongoUri);
		bookCollectionStore.save(collection(COLLECTION_OWN_DB, PUBLISHER_OWN_DB, "OWNDB2024"));

		Page<BookCollection> page = bookCollectionStore.findByPublisherId(PUBLISHER_OWN_DB, PageRequest.of(0, 20));
		assertThat(page.getContent()).extracting(BookCollection::getId).containsExactly(COLLECTION_OWN_DB);

		assertThat(bookCollectionStore.findByPublisherIdAndCode(PUBLISHER_OWN_DB, "OWNDB2024")).isPresent();
		assertThat(bookCollectionStore.countByPublisherId(PUBLISHER_OWN_DB)).isEqualTo(1L);
	}

	@Test
	@DisplayName("findAll fans out across the shared database and every publisher's own database")
	void findAllFansOutAcrossEveryDatabase() {
		String ownMongoUri = OWN_MONGO.getConnectionString() + "/pub_collectionstore_owndb_test3";
		publisher(PUBLISHER_SHARED, "COLSTORESHARED", null);
		publisher(PUBLISHER_OWN_DB, "COLSTOREOWNDB", ownMongoUri);

		bookCollectionStore.save(collection(COLLECTION_SHARED, PUBLISHER_SHARED, "SHARED2024"));
		bookCollectionStore.save(collection(COLLECTION_OWN_DB, PUBLISHER_OWN_DB, "OWNDB2024"));

		Page<BookCollection> page = bookCollectionStore.findAll(PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(BookCollection::getId).contains(COLLECTION_SHARED, COLLECTION_OWN_DB);
	}

	@Test
	@DisplayName("saving a collection for a publisher that does not exist is rejected")
	void rejectsASaveForAnUnknownPublisher() {
		assertThatThrownBy(() -> bookCollectionStore.save(collection(COLLECTION_SHARED, "pub_does_not_exist", "X")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not reference an existing publisher");
	}

	private void publisher(String id, String code, String mongoUri) {
		Publisher publisher = new Publisher(id, code, code, null, null, RecordStatus.ACTIVE, null, null);
		publisher.setMongoUri(mongoUri);
		publisherRepository.save(publisher);
	}

	private static BookCollection collection(String id, String publisherId, String code) {
		return new BookCollection(id, publisherId, code, "Fixture Collection " + code, null);
	}

}
