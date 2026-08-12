package com.tf.reader.catalogue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.Institution;

public interface InstitutionRepository extends MongoRepository<Institution, String> {

	Optional<Institution> findByCode(String code);

	List<Institution> findAllBy(TextCriteria criteria);

}
