package com.tnf.reader.catalogue.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tnf.reader.catalogue.entity.Publisher;

public interface PublisherRepository extends MongoRepository<Publisher, String> {

	Optional<Publisher> findByCode(String code);

}
