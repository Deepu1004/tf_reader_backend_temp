package com.tf.reader.catalogue.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tf.reader.ContainerisedInfrastructure;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemPublisherIndex;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.common.model.RecordStatus;

/**
 * {@code ItemPublisherIndexSync} listens for every {@code CatalogueItem} save regardless of which
 * service made it, so this exercises the listener directly through the repository rather than
 * through any one caller.
 */
@SpringBootTest(properties = {
		"tnf.auth.jwt.secret=" + ContainerisedInfrastructure.JWT_SECRET,
		"tnf.seed.enabled=false" })
class ItemPublisherIndexSyncIT extends ContainerisedInfrastructure {

	private static final String PUBLISHER_ID = "pub_itemindexsync";
	private static final String OTHER_PUBLISHER_ID = "pub_itemindexsync_2";
	private static final String ITEM_ID = "item_itemindexsync";

	@Autowired private CatalogueItemRepository catalogueItemRepository;
	@Autowired private PublisherRepository publisherRepository;
	@Autowired private ItemPublisherIndexRepository itemPublisherIndexRepository;

	@AfterEach
	void removeWhatThisTestWrote() {
		catalogueItemRepository.deleteById(ITEM_ID);
		itemPublisherIndexRepository.deleteById(ITEM_ID);
		publisherRepository.deleteById(PUBLISHER_ID);
		publisherRepository.deleteById(OTHER_PUBLISHER_ID);
	}

	@Test
	@DisplayName("saving a catalogue item upserts its entry in the item-publisher index")
	void upsertsItemPublisherIndexWhenCatalogueItemIsSaved() {
		publisher(PUBLISHER_ID, "ITEMINDEXSYNC");
		catalogueItemRepository.save(item(ITEM_ID, PUBLISHER_ID));

		ItemPublisherIndex indexed = itemPublisherIndexRepository.findById(ITEM_ID).orElseThrow();
		assertThat(indexed.getPublisherId()).isEqualTo(PUBLISHER_ID);
	}

	@Test
	@DisplayName("re-parenting an item to a different publisher overwrites its index entry")
	void overwritesExistingIndexEntryWhenCatalogueItemPublisherChanges() {
		publisher(PUBLISHER_ID, "ITEMINDEXSYNC");
		publisher(OTHER_PUBLISHER_ID, "ITEMINDEXSYNC2");

		CatalogueItem item = catalogueItemRepository.save(item(ITEM_ID, PUBLISHER_ID));
		item.setPublisherId(OTHER_PUBLISHER_ID);
		catalogueItemRepository.save(item);

		ItemPublisherIndex indexed = itemPublisherIndexRepository.findById(ITEM_ID).orElseThrow();
		assertThat(indexed.getPublisherId()).isEqualTo(OTHER_PUBLISHER_ID);
	}

	private void publisher(String id, String code) {
		publisherRepository.save(new Publisher(id, code, code, null, null, RecordStatus.ACTIVE, null, null));
	}

	private static CatalogueItem item(String id, String publisherId) {
		CatalogueItem item = new CatalogueItem();
		item.setId(id);
		item.setPublisherId(publisherId);
		item.setTitle("Item Publisher Index Fixture");
		item.setContentType(ContentType.PDF);
		item.setAccessTier(AccessTier.SUBSCRIPTION);
		item.setStatus(ItemStatus.DRAFT);
		item.setContentState(ContentState.NONE);
		return item;
	}

}
