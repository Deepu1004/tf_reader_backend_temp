package com.tf.reader.common.mongo;

import java.util.concurrent.ConcurrentHashMap;

import org.bson.Document;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.stereotype.Component;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

/**
 * One cached {@link MongoDatabaseFactory} per publisher who has configured their own MongoDB
 * connection. Shared by the crypto module ({@code PublisherVaultKeyStore}, for a publisher's own
 * vault key) and the catalogue database-routing layer ({@code TenantAwareMongoDatabaseFactory}) -
 * one cache, not two, since both are asking the same question: "which Mongo does this publisher
 * use, if not T&F's shared one."
 *
 * <p>Opt-in only: nothing calls this unless a {@code Publisher.mongoUri} is already set. A
 * publisher with no {@code mongoUri} never reaches this class at all.
 */
@Component
public class PublisherMongoClients {

	private record CachedFactory(String uri, MongoClient client, MongoDatabaseFactory factory) {
	}

	private final ConcurrentHashMap<String, CachedFactory> factories = new ConcurrentHashMap<>();

	/**
	 * A proper Spring Data {@link MongoDatabaseFactory} for the publisher's own database -
	 * everything a {@code MongoTemplate} needs (sessions, exception translation, codec registry
	 * included), not just a raw driver handle.
	 */
	public MongoDatabaseFactory factoryFor(String publisherId, String mongoUri) {
		return factories.compute(publisherId, (id, existing) -> {
			if (existing != null && existing.uri().equals(mongoUri)) {
				return existing;
			}
			if (existing != null) {
				existing.client().close();
			}
			MongoClient client = MongoClients.create(mongoUri);
			return new CachedFactory(mongoUri, client, MongoDatabaseFactory.create(client, databaseNameOf(mongoUri)));
		}).factory();
	}

	/** The publisher's own database, connecting (or reusing a cached connection) as needed. */
	public MongoDatabase databaseFor(String publisherId, String mongoUri) {
		return factoryFor(publisherId, mongoUri).getMongoDatabase();
	}

	/**
	 * Drops the cached connection for a publisher, so the next call to {@link #factoryFor}/
	 * {@link #databaseFor} reconnects with whatever {@code mongoUri} is current. Called whenever a
	 * publisher changes or clears their Mongo connection through the admin endpoint.
	 */
	public void invalidate(String publisherId) {
		CachedFactory removed = factories.remove(publisherId);
		if (removed != null) {
			removed.client().close();
		}
	}

	/**
	 * A short-lived connection attempt, used only to report {@code connectionHealth} back to the
	 * caller setting a new {@code mongoUri} - never reused, so a bad URI never pollutes the cache.
	 */
	public boolean ping(String mongoUri) {
		try (MongoClient probe = MongoClients.create(mongoUri)) {
			probe.getDatabase(databaseNameOf(mongoUri)).runCommand(new Document("ping", 1));
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static String databaseNameOf(String mongoUri) {
		String database = new ConnectionString(mongoUri).getDatabase();
		if (database == null || database.isBlank()) {
			throw new IllegalArgumentException("mongoUri must name a database, e.g. .../pub01");
		}
		return database;
	}

}
