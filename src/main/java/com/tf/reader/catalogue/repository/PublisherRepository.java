package com.tf.reader.catalogue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.Publisher;

public interface PublisherRepository extends MongoRepository<Publisher, String> {

	Optional<Publisher> findByCode(String code);

	/** Every publisher who has opted into their own MongoDB - who a fan-out query has to also ask. */
	List<Publisher> findByMongoUriIsNotNull();

}
