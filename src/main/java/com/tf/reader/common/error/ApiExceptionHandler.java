package com.tf.reader.common.error;

import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.tf.reader.admin.exception.InvalidCredentialsException;
import com.tf.reader.admin.exception.InvalidRefreshTokenException;

/**
 * Turns application exceptions into RFC 9457 responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own failures, including request
 * body validation, already produce a {@link ProblemDetail} in the same shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * Both authentication failures collapse to the same opaque 401. The exception message is
	 * deliberately not copied into the response.
	 */
	@ExceptionHandler({ InvalidCredentialsException.class, InvalidRefreshTokenException.class })
	ProblemDetail handleAuthenticationFailure(RuntimeException exception) {
		return ProblemDetails.unauthorized("Authentication failed.");
	}

	/** Method-security denials thrown from within a controller invocation. */
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		return ProblemDetails.forbidden("You do not have permission to access this resource.");
	}

}
