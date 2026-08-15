package com.tf.reader.admin.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per sign in, as the contract's ninth collection.
 *
 * <p>Login inserts a row, refresh revokes it and inserts a new one, logout sets {@link #revokedAt}.
 * The refresh token is opaque, so this row is the only thing that gives it meaning: every refresh is
 * a lookup by {@link #refreshTokenHash}.
 */
@Document(collection = "adminSessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminSession {

	/** Prefixed {@code sess_}, and issued as the {@code sid} claim of the access token. */
	@Id
	private String id;

	@Indexed(name = "adminUserId_idx")
	private String adminUserId;

	/** SHA-256 of the token, lowercase hex. The raw token is never stored. */
	@Indexed(name = "refreshTokenHash_unique", unique = true)
	private String refreshTokenHash;

	private Instant issuedAt;

	/** Mongo removes the document once this passes, bounding growth without a cleanup job. */
	@Indexed(name = "expiresAt_ttl", expireAfter = "0s")
	private Instant expiresAt;

	/** Once set, the row can never be exchanged again and its access token is rejected too. */
	private Instant revokedAt;

	private String revokedReason;

}
