package com.tf.reader.catalogue.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.CollectionPublisherIndex;

public interface CollectionPublisherIndexRepository extends MongoRepository<CollectionPublisherIndex, String> {
}
