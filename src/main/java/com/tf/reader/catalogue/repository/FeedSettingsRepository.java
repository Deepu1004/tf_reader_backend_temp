package com.tf.reader.catalogue.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.catalogue.entity.FeedSettings;

public interface FeedSettingsRepository extends MongoRepository<FeedSettings, String> {

	Optional<FeedSettings> findByInstitutionId(String institutionId);

}
