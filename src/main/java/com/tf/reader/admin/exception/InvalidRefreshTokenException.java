package com.tf.reader.admin.exception;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Raised whenever a refresh token cannot be exchanged. The reason is recorded server side but never
 * returned, so the client learns only that the token was rejected.
 *
 * <p>Carries {@code UNAUTHENTICATED} so it renders through the shared error envelope. The reason is
 * kept off {@code getMessage()} for the same purpose: unknown, expired, already used and revoked must
 * look identical on the wire, and the envelope returns the message.
 */
public class InvalidRefreshTokenException extends ApiException {

	/** The contract's answer for all four failures: the only cure is signing in again. */
	private static final String PUBLIC_MESSAGE = "Sign in again.";

	private final String serverSideReason;

	public InvalidRefreshTokenException(String serverSideReason) {
		super(ErrorCode.UNAUTHENTICATED, PUBLIC_MESSAGE);
		this.serverSideReason = serverSideReason;
	}

	public InvalidRefreshTokenException(String serverSideReason, Throwable cause) {
		this(serverSideReason);
		initCause(cause);
	}

	/** Diagnostic detail for logs. Never put this in a response. */
	public String getServerSideReason() {
		return this.serverSideReason;
	}

}
