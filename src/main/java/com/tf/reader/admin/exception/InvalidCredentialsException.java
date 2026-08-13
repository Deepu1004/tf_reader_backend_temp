package com.tf.reader.admin.exception;

/**
 * Raised for every failed login, whatever the cause, so the response cannot be used to enumerate
 * accounts or probe their status.
 */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid email or password.");
	}

}
