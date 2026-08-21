package com.tf.reader.catalogue.entity;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.repository.PublisherRepository;

@Component
public class CatalogueItemPersistenceGuard implements BeforeConvertCallback<CatalogueItem> {

	private final PublisherRepository publisherRepository;

	public CatalogueItemPersistenceGuard(@Lazy PublisherRepository publisherRepository) {
		this.publisherRepository = publisherRepository;
	}

	@Override
	public CatalogueItem onBeforeConvert(CatalogueItem item, String collection) {
		if (item.getPublisherId() == null || !publisherRepository.existsById(item.getPublisherId())) {
			throw new IllegalArgumentException("CatalogueItem.publisherId does not reference an existing publisher");
		}
		return item;
	}

}
