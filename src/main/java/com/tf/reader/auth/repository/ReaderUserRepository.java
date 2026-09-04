package com.tf.reader.auth.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.auth.entity.ReaderUser;

// Mongo repository for ReaderUser documents.
public interface ReaderUserRepository extends MongoRepository<ReaderUser, String> {

	Optional<ReaderUser> findByEmailAndInstitutionId(String email, String institutionId);

	/** The individual counterpart: no institution, so no pair to key by - just the email. */
	Optional<ReaderUser> findByEmailAndInstitutionIdIsNull(String email);
}
