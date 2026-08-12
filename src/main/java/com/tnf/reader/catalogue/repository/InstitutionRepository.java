package com.tnf.reader.catalogue.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tnf.reader.catalogue.entity.Institution;

public interface InstitutionRepository extends MongoRepository<Institution, String> {

	Optional<Institution> findByCode(String code);

}
