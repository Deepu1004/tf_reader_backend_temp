package com.tf.reader.admin.exception;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Raised for every failed login, whatever the cause, so the response cannot be used to enumerate
 * accounts or probe their status.
 *
 * <p>Carries {@code UNAUTHENTICATED} so it renders through the shared error envelope rather than a
 * second shape of its own. The message names neither the email nor the password, because the three
 * ways to fail must be indistinguishable.
 */
public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super(ErrorCode.UNAUTHENTICATED, "Authentication failed.");
	}

}
