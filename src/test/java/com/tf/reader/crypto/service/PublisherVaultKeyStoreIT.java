package com.tf.reader.crypto.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.mongo.PublisherMongoClients;

/**
 * Confirms both storage locations {@code PublisherVaultKeyStore} can use: T&F's shared database
 * when the publisher has no Mongo of their own, and the publisher's own Mongo (a second,
 * locally-started container standing in for one) once they've configured a {@code mongoUri}.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class PublisherVaultKeyStoreIT extends ContainerisedInfrastructure {

	// Stands in for "a publisher's own MongoDB," independent of the shared suite container above.
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

	private static final String PUBLISHER_NO_OWN_MONGO = "pub_vaultkey_shared";
	private static final String PUBLISHER_WITH_OWN_MONGO = "pub_vaultkey_owndb";

	@Autowired private PublisherVaultKeyStore vaultKeyStore;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private PublisherMongoClients publisherMongoClients;
	@Autowired private MongoTemplate mongoTemplate;

	@AfterEach
	void removeWhatThisTestWrote() {
		vaultKeyStore.clear(PUBLISHER_NO_OWN_MONGO);
		vaultKeyStore.clear(PUBLISHER_WITH_OWN_MONGO);
		publisherMongoClients.invalidate(PUBLISHER_WITH_OWN_MONGO);
		publisherRepository.deleteById(PUBLISHER_NO_OWN_MONGO);
		publisherRepository.deleteById(PUBLISHER_WITH_OWN_MONGO);
	}

	@Test
	@DisplayName("a publisher with no Mongo of their own stores their key in the shared database")
	void storesInSharedDatabaseWhenPublisherHasNoOwnMongo() {
		publisher(PUBLISHER_NO_OWN_MONGO, "VAULTSHARED", null);
		SecretKey key = aesKey();

		vaultKeyStore.save(PUBLISHER_NO_OWN_MONGO, key);

		Optional<SecretKey> found = vaultKeyStore.find(PUBLISHER_NO_OWN_MONGO);
		assertThat(found).isPresent();
		assertThat(found.get().getEncoded()).isEqualTo(key.getEncoded());
		assertThat(mongoTemplate.getCollection("publisherVaultKeys")
				.find(new org.bson.Document("_id", PUBLISHER_NO_OWN_MONGO)).first()).isNotNull();
	}

	@Test
	@DisplayName("a publisher with their own Mongo stores their key there instead")
	void storesInOwnDatabaseWhenPublisherHasConfiguredMongo() {
		publisher(PUBLISHER_WITH_OWN_MONGO, "VAULTOWNDB", OWN_MONGO.getConnectionString() + "/pub_owndb_test");
		SecretKey key = aesKey();

		vaultKeyStore.save(PUBLISHER_WITH_OWN_MONGO, key);

		Optional<SecretKey> found = vaultKeyStore.find(PUBLISHER_WITH_OWN_MONGO);
		assertThat(found).isPresent();
		assertThat(found.get().getEncoded()).isEqualTo(key.getEncoded());

		// Not in the shared database at all - it genuinely lives in the publisher's own Mongo.
		assertThat(mongoTemplate.getCollection("publisherVaultKeys")
				.find(new org.bson.Document("_id", PUBLISHER_WITH_OWN_MONGO)).first()).isNull();
	}

	@Test
	@DisplayName("no key configured for a publisher means find() is empty")
	void findIsEmptyWhenNoKeyConfigured() {
		publisher(PUBLISHER_NO_OWN_MONGO, "VAULTSHARED", null);

		assertThat(vaultKeyStore.find(PUBLISHER_NO_OWN_MONGO)).isEmpty();
	}

	private void publisher(String id, String code, String mongoUri) {
		Publisher publisher = new Publisher(id, code, code, null, null, RecordStatus.ACTIVE, null, null);
		publisher.setMongoUri(mongoUri);
		publisherRepository.save(publisher);
	}

	private static SecretKey aesKey() {
		byte[] raw = new byte[32];
		java.util.Arrays.fill(raw, (byte) 7);
		return new SecretKeySpec(raw, "AES");
	}

}
