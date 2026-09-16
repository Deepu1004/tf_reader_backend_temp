package com.tf.reader.catalogue.service;

import java.time.Clock;

import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.entity.BookCollection;
import com.tf.reader.catalogue.entity.CollectionPublisherIndex;
import com.tf.reader.catalogue.repository.CollectionPublisherIndexRepository;

/**
 * {@link ItemPublisherIndexSync}'s counterpart for {@link BookCollection}. Keeps
 * {@link CollectionPublisherIndex} up to date for every save through the shared repository,
 * including {@code DemoDataSeeder}'s, without editing it - the same reasoning as the item version.
 *
 * <p>Write side only for a save through the shared repository. {@code BookCollectionStore} updates
 * the index by hand for a save that routes to a publisher's own database instead, since this
 * listener never sees those.
 */
@Component
class CollectionPublisherIndexSync extends AbstractMongoEventListener<BookCollection> {

	private final CollectionPublisherIndexRepository collectionPublisherIndex;
	private final Clock clock;

	CollectionPublisherIndexSync(CollectionPublisherIndexRepository collectionPublisherIndex, Clock clock) {
		this.collectionPublisherIndex = collectionPublisherIndex;
		this.clock = clock;
	}

	@Override
	public void onAfterSave(AfterSaveEvent<BookCollection> event) {
		BookCollection collection = event.getSource();
		collectionPublisherIndex.save(
				new CollectionPublisherIndex(collection.getId(), collection.getPublisherId(), clock.instant()));
	}

}
