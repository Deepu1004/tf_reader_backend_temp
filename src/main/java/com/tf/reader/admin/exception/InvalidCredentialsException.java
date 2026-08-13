package com.tf.reader.admin.exception;

/**
 * Raised for every failed login, whatever the underlying cause.
 *
 * <p>Unknown email, wrong password and a non-active account all raise this same exception with the
 * same message, so the response cannot be used to enumerate accounts or probe their status.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid email or password.");
	}

}
