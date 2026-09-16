package com.tf.reader.catalogue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.CatalogueItem;

/**
 * Multi-tenancy's per-publisher database split has nowhere correct to put a record with no
 * {@code publisherId}, so this checks the invariant holds before that split ever happens.
 *
 * <p>{@code CatalogueItemPersistenceGuard} already stops a missing {@code publisherId} from being
 * written through the repository, but a raw driver write (a manual fixup, a migration script, data
 * from before the guard existed) bypasses it. Written straight against the collection with the
 * MongoDB driver, not through the repository, so the guard cannot intercept it - proving the query
 * below would actually catch a real offender rather than passing by construction.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class PublisherIdIntegrityIT extends ContainerisedInfrastructure {

	@Autowired private MongoTemplate mongoTemplate;

	private static final String STRAY_ITEM_ID = "item_no_publisher_fixture";

	@AfterEach
	void removeWhatThisTestWrote() {
		mongoTemplate.getCollection("catalogueItems").deleteOne(new Document("_id", STRAY_ITEM_ID));
	}

	@Test
	@DisplayName("no catalogue item in the database is missing publisherId")
	void findsNoCatalogueItemsMissingPublisherId() {
		assertThat(missingPublisherId(CatalogueItem.class)).isEmpty();
	}

	@Test
	@DisplayName("no book collection in the database is missing publisherId")
	void findsNoBookCollectionsMissingPublisherId() {
		assertThat(missingPublisherId(BookCollection.class)).isEmpty();
	}

	@Test
	@DisplayName("the missing-publisherId query actually catches a document that has none")
	void queryCatchesADocumentWrittenWithoutPublisherId() {
		mongoTemplate.getCollection("catalogueItems")
				.insertOne(new Document("_id", STRAY_ITEM_ID).append("title", "No publisher"));

		assertThat(missingPublisherId(CatalogueItem.class))
				.extracting(CatalogueItem::getId)
				.containsExactly(STRAY_ITEM_ID);
	}

	private <T> java.util.List<T> missingPublisherId(Class<T> type) {
		Criteria noPublisherId = new Criteria().orOperator(
				Criteria.where("publisherId").exists(false),
				Criteria.where("publisherId").is(null),
				Criteria.where("publisherId").is(""));
		return mongoTemplate.find(Query.query(noPublisherId), type);
	}

}
