package com.tf.reader.auth.repository;

import java.time.Instant;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.auth.entity.ReaderSession;

public interface ReaderSessionRepository
		extends MongoRepository<ReaderSession, String>, ReaderSessionRepositoryCustom {

	// The institution's concurrent-seat count: every session not yet revoked and not yet
	// expired. Read at sign-in time only, and only for a device with no seat of its own already -
	// see SamlUserMapper.
	long countByInstitutionIdAndRevokedAtIsNullAndExpiresAtAfter(String institutionId, Instant now);

}
