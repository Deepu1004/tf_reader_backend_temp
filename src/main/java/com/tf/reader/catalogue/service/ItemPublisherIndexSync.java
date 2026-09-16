package com.tf.reader.catalogue.service;

import java.time.Clock;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ItemPublisherIndex;
import com.tf.reader.catalogue.repository.ItemPublisherIndexRepository;

/**
 * Keeps {@link ItemPublisherIndex} up to date without touching any of the five places that save a
 * {@link CatalogueItem} today: Spring Data calls every {@code AbstractMongoEventListener} for the
 * type it declares, on every save, regardless of which service made the call.
 *
 * <p>Write side only. Nothing reads from {@link ItemPublisherIndexRepository} yet.
 */
@Component
class ItemPublisherIndexSync extends AbstractMongoEventListener<CatalogueItem> {

	private final ItemPublisherIndexRepository itemPublisherIndex;
	private final Clock clock;

	ItemPublisherIndexSync(ItemPublisherIndexRepository itemPublisherIndex, Clock clock) {
		this.itemPublisherIndex = itemPublisherIndex;
		this.clock = clock;
	}

	@Override
	public void onAfterSave(AfterSaveEvent<CatalogueItem> event) {
		CatalogueItem item = event.getSource();
		itemPublisherIndex.save(new ItemPublisherIndex(item.getId(), item.getPublisherId(), clock.instant()));
	}

}
