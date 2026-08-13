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
 * Server-side state for one admin login session.
 *
 * <p>The document id is the session id ({@code sid}) carried by both the access token and the
 * refresh token, so it survives refresh-token rotation. Exactly one refresh token is valid per
 * session at any moment: the one whose {@code jti} equals {@link #currentRefreshJti}. Rotation
 * replaces that pair atomically, which is what makes replay of a superseded refresh token
 * detectable.
 *
 * <p>The refresh token itself is never stored. Only a SHA-256 fingerprint is kept, so a leaked
 * database dump does not yield usable refresh tokens.
 */
@Document(collection = "adminSessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminSession {

	/** The session id, also issued as the {@code sid} claim. */
	@Id
	private String id;

	@Indexed(name = "adminUserId_idx")
	private String adminUserId;

	/** {@code jti} of the only refresh token currently accepted for this session. */
	private String currentRefreshJti;

	/** SHA-256 fingerprint of that refresh token. The raw token is never persisted. */
	private String currentRefreshTokenHash;

	private Instant issuedAt;
	private Instant lastRotatedAt;

	/** Mongo removes the document once this passes, bounding growth without a cleanup job. */
	@Indexed(name = "expiresAt_ttl", expireAfter = "0s")
	private Instant expiresAt;

	private Instant revokedAt;
	private String revokedReason;

}
