package com.tf.reader.catalogue.entity;

import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

@Component
public class FeedSettingsPersistenceGuard implements BeforeConvertCallback<FeedSettings> {

	private static final int REQUIRED_SHELF_COUNT = 3;
	private static final int MAX_ITEMS_PER_SHELF = 50;

	@Override
	public FeedSettings onBeforeConvert(FeedSettings feedSettings, String collection) {
		if (feedSettings.getShelves() == null || feedSettings.getShelves().size() != REQUIRED_SHELF_COUNT) {
			throw new IllegalArgumentException("FeedSettings must have exactly " + REQUIRED_SHELF_COUNT + " shelves");
		}
		for (Shelf shelf : feedSettings.getShelves()) {
			if (shelf.getItemIds() != null && shelf.getItemIds().size() > MAX_ITEMS_PER_SHELF) {
				throw new IllegalArgumentException("Shelf must not exceed " + MAX_ITEMS_PER_SHELF + " items");
			}
		}
		return feedSettings;
	}

}
