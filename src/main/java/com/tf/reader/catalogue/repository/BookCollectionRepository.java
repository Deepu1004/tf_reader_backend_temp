package com.tf.reader.catalogue.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.BookCollection;

public interface BookCollectionRepository extends MongoRepository<BookCollection, String> {

	Optional<BookCollection> findByPublisherIdAndCode(String publisherId, String code);

}
