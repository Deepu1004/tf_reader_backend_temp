package com.tf.reader.admin.exception;

/**
 * Raised whenever a refresh token cannot be exchanged. The reason is recorded server side but never
 * returned, so the client learns only that the token was rejected.
 */
public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException(String serverSideReason) {
		super(serverSideReason);
	}

	public InvalidRefreshTokenException(String serverSideReason, Throwable cause) {
		super(serverSideReason, cause);
	}

}
