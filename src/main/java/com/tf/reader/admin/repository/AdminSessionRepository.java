package com.tf.reader.admin.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.admin.entity.AdminSession;

public interface AdminSessionRepository extends MongoRepository<AdminSession, String>, AdminSessionRepositoryCustom {

	/** True only for a session that exists, has not been revoked and has not expired. */
	boolean existsByIdAndRevokedAtIsNullAndExpiresAtAfter(String id, Instant now);

}
