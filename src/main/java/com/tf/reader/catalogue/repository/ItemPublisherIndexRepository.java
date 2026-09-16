package com.tf.reader.catalogue.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.ItemPublisherIndex;

public interface ItemPublisherIndexRepository extends MongoRepository<ItemPublisherIndex, String> {
}
