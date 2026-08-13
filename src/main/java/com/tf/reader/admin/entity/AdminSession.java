package com.tf.reader.admin.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Server-side state for one admin login session.
 *
 * <p>The refresh token is opaque, so this document is the only thing that gives it meaning: every
 * refresh is a lookup by {@link #currentRefreshTokenHash}. Exactly one token is current at a time and
 * rotation replaces it atomically, which is what makes replay of a superseded token detectable.
 */
@Document(collection = "adminSessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminSession {

	/** Also issued as the {@code sid} claim of the access token. */
	@Id
	private String id;

	@Indexed(name = "adminUserId_idx")
	private String adminUserId;

	/** SHA-256 of the only refresh token this session currently accepts. The raw token is never stored. */
	@Indexed(name = "refreshTokenHash_unique", unique = true)
	private String currentRefreshTokenHash;

	/**
	 * Hashes this session has already rotated away from. Presenting one is the signature of a stolen
	 * token, and without this an opaque token would become indistinguishable from one that never
	 * existed the moment it was rotated.
	 */
	@Indexed(name = "supersededRefreshTokenHashes_idx")
	private List<String> supersededRefreshTokenHashes = new ArrayList<>();

	private Instant issuedAt;
	private Instant lastRotatedAt;

	/** Mongo removes the document once this passes, bounding growth without a cleanup job. */
	@Indexed(name = "expiresAt_ttl", expireAfter = "0s")
	private Instant expiresAt;

	private Instant revokedAt;
	private String revokedReason;

}
