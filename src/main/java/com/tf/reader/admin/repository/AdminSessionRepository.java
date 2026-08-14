package com.tf.reader.admin.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.admin.entity.AdminSession;

public interface AdminSessionRepository extends MongoRepository<AdminSession, String>, AdminSessionRepositoryCustom {

	boolean existsByIdAndRevokedAtIsNullAndExpiresAtAfter(String id, Instant now);

	/** The lookup every refresh starts from, since an opaque token carries no session id. */
	Optional<AdminSession> findByRefreshTokenHash(String refreshTokenHash);

}
