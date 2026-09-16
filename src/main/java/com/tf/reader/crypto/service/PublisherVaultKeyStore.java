package com.tf.reader.crypto.service;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.mongodb.client.model.ReplaceOptions;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.mongo.PublisherMongoClients;

import lombok.RequiredArgsConstructor;

/**
 * Where a publisher's own vault key actually lives, opt-in per publisher:
 *
 * <ul>
 *   <li>Publisher has their own {@code mongoUri} configured: the key lives in <em>their</em>
 *   database, in a single-document {@code vaultKeys} collection. Revoking T&F's access to that
 *   Mongo revokes the key at the same instant - the whole point of this plan.</li>
 *   <li>No {@code mongoUri} yet: the key lives in T&F's shared database, in a dedicated
 *   {@code publisherVaultKeys} collection, one document per publisher - the only place available
 *   until the publisher also sets up their own Mongo.</li>
 * </ul>
 *
 * <p>Empty from {@link #find} means "no vault key configured," which {@link MongoBackedKmsClient}
 * reads as "use T&F's shared master key."
 */
@Service
@RequiredArgsConstructor
class PublisherVaultKeyStore {

	private static final String OWN_DB_COLLECTION = "vaultKeys";
	private static final String OWN_DB_DOCUMENT_ID = "vault-key";
	private static final String SHARED_COLLECTION = "publisherVaultKeys";

	private final PublisherRepository publisherRepository;
	private final PublisherMongoClients publisherMongoClients;
	private final MongoTemplate mongoTemplate;

	Optional<SecretKey> find(String publisherId) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher == null) {
			return Optional.empty();
		}
		Document stored = hasText(publisher.getMongoUri())
				? ownDatabase(publisher).getCollection(OWN_DB_COLLECTION)
						.find(new Document("_id", OWN_DB_DOCUMENT_ID)).first()
				: sharedCollection().find(new Document("_id", publisherId)).first();
		return Optional.ofNullable(stored).map(PublisherVaultKeyStore::toKey);
	}

	/**
	 * Writes to whichever location applies right now, per the publisher's current
	 * {@code mongoUri}, and removes any stale copy left behind in the other location - a publisher
	 * who later configures their own Mongo should not leave their old key sitting in T&F's shared
	 * database.
	 */
	void save(String publisherId, SecretKey key) {
		Publisher publisher = publisherRepository.findById(publisherId)
				.orElseThrow(() -> new IllegalArgumentException("No such publisher: " + publisherId));

		Document toStore = new Document("keyBase64", Base64.getEncoder().encodeToString(key.getEncoded()))
				.append("updatedAt", Instant.now());

		if (hasText(publisher.getMongoUri())) {
			ownDatabase(publisher).getCollection(OWN_DB_COLLECTION).replaceOne(
					new Document("_id", OWN_DB_DOCUMENT_ID), toStore.append("_id", OWN_DB_DOCUMENT_ID),
					new ReplaceOptions().upsert(true));
			sharedCollection().deleteOne(new Document("_id", publisherId));
		} else {
			sharedCollection().replaceOne(new Document("_id", publisherId),
					toStore.append("_id", publisherId), new ReplaceOptions().upsert(true));
		}
	}

	/** Removes the key from wherever it currently lives, reverting the publisher to T&F's shared master key. */
	void clear(String publisherId) {
		Publisher publisher = publisherRepository.findById(publisherId).orElse(null);
		if (publisher != null && hasText(publisher.getMongoUri())) {
			ownDatabase(publisher).getCollection(OWN_DB_COLLECTION).deleteOne(new Document("_id", OWN_DB_DOCUMENT_ID));
		}
		sharedCollection().deleteOne(new Document("_id", publisherId));
	}

	private com.mongodb.client.MongoDatabase ownDatabase(Publisher publisher) {
		return publisherMongoClients.databaseFor(publisher.getId(), publisher.getMongoUri());
	}

	private com.mongodb.client.MongoCollection<Document> sharedCollection() {
		return mongoTemplate.getCollection(SHARED_COLLECTION);
	}

	private static SecretKey toKey(Document doc) {
		return new SecretKeySpec(Base64.getDecoder().decode(doc.getString("keyBase64")), "AES");
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
