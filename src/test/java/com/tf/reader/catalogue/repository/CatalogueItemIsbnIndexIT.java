package com.tf.reader.catalogue.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.common.model.RecordStatus;

/**
 * The unique sparse index on {@code catalogueItems.isbn} is the backstop under
 * {@code CatalogueItemAdminService}'s duplicate check, which is check-then-act and so cannot stop
 * two simultaneous creates on its own.
 *
 * <p>Real Mongo, because this is a test about index behaviour: a mocked repository would prove
 * nothing. The first test is the important one. Most catalogue items have no ISBN, so if Spring
 * Data ever started writing {@code isbn: null} rather than omitting the field, the index would
 * stop being sparse in practice and the second ISBN-less book in the system would be rejected -
 * taking the demo seeder down with it.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class CatalogueItemIsbnIndexIT extends ContainerisedInfrastructure {

	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private PublisherRepository publisherRepository;

	@AfterEach
	void removeWhatThisTestWrote() {
		// The Mongo container is shared by the whole suite and never reset, so a stray item
		// carrying one of these ISBNs would fail an unrelated test's index creation later.
		catalogueItemRepository.deleteAll(catalogueItemRepository.findAll().stream()
				.filter(item -> PUBLISHER_ID.equals(item.getPublisherId()))
				.toList());
		publisherRepository.deleteById(PUBLISHER_ID);
	}

	@Test
	@DisplayName("any number of items may have no ISBN at all")
	void savesManyItemsWithNoIsbnUnderTheUniqueIndex() {
		String publisherId = newPublisher();

		catalogueItemRepository.save(item(publisherId, "First untitled", null));
		catalogueItemRepository.save(item(publisherId, "Second untitled", null));
		catalogueItemRepository.save(item(publisherId, "Third untitled", null));

		List<CatalogueItem> saved = catalogueItemRepository.findByPublisherIdAndStatus(publisherId, ItemStatus.DRAFT);
		assertThat(saved).hasSize(3);
	}

	@Test
	@DisplayName("two items cannot share an ISBN even when the service check is bypassed")
	void rejectsASecondItemWithTheSameIsbn() {
		String publisherId = newPublisher();
		catalogueItemRepository.save(item(publisherId, "Rights for Robots", "9781509930029"));

		assertThatThrownBy(() -> catalogueItemRepository.save(item(publisherId, "A Copy", "9781509930029")))
				.isInstanceOf(DuplicateKeyException.class);
	}

	// A fixed id rather than a generated one, so the cleanup above can find everything this test
	// wrote in a container it shares with the rest of the suite.
	private static final String PUBLISHER_ID = "pub_isbnidx";

	private String newPublisher() {
		return publisherRepository
				.save(new Publisher(PUBLISHER_ID, "ISBNIDX", "ISBN Index Fixture Press", null, null,
						RecordStatus.ACTIVE, null, null))
				.getId();
	}

	private static CatalogueItem item(String publisherId, String title, String isbn) {
		CatalogueItem item = new CatalogueItem();
		item.setPublisherId(publisherId);
		item.setTitle(title);
		item.setIsbn(isbn);
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.SUBSCRIPTION);
		item.setStatus(ItemStatus.DRAFT);
		item.setContentState(ContentState.NONE);
		return item;
	}

}
