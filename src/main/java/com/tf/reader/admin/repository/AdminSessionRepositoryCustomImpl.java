package com.tf.reader.admin.repository;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.tf.reader.admin.entity.AdminSession;

public class AdminSessionRepositoryCustomImpl implements AdminSessionRepositoryCustom {

	private final MongoTemplate mongoTemplate;

	public AdminSessionRepositoryCustomImpl(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public Optional<AdminSession> rotateRefreshToken(String presentedTokenHash, String newTokenHash, Instant now) {
		// Every precondition is in the query, so check-and-swap is one atomic document update. The
		// hash is unique across sessions, so it identifies the session on its own.
		Query guard = new Query(where("currentRefreshTokenHash").is(presentedTokenHash)
				.and("revokedAt").is(null)
				.and("expiresAt").gt(now));

		Update rotation = new Update()
				.set("currentRefreshTokenHash", newTokenHash)
				.set("lastRotatedAt", now)
				.push("supersededRefreshTokenHashes", presentedTokenHash);

		AdminSession rotated = this.mongoTemplate.findAndModify(guard, rotation,
				FindAndModifyOptions.options().returnNew(true), AdminSession.class);

		return Optional.ofNullable(rotated);
	}

	@Override
	public boolean revoke(String sessionId, String reason, Instant now) {
		Query notYetRevoked = new Query(where("_id").is(sessionId).and("revokedAt").is(null));
		Update revocation = new Update().set("revokedAt", now).set("revokedReason", reason);

		return this.mongoTemplate.updateFirst(notYetRevoked, revocation, AdminSession.class).getModifiedCount() > 0;
	}

}
