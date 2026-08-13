package com.tf.reader.common.error;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.tf.reader.admin.exception.InvalidCredentialsException;
import com.tf.reader.admin.exception.InvalidRefreshTokenException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Turns application exceptions into the contract's {@link ApiError} envelope.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring's own failures, including body
 * validation, are caught too and rewritten into the contract shape by
 * {@link #handleExceptionInternal}. There is exactly one error format on the wire.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	/** Both collapse to the same opaque 401; the exception message never reaches the response. */
	@ExceptionHandler({ InvalidCredentialsException.class, InvalidRefreshTokenException.class })
	ResponseEntity<ApiError> handleAuthenticationFailure(RuntimeException exception,
			HttpServletRequest request) {

		return json(ApiErrors.unauthenticated(request.getRequestURI()));
	}

	/** Method-security denials thrown from within a controller invocation. */
	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
		return json(ApiErrors.forbidden(request.getRequestURI()));
	}

	/** Server errors are left alone: the contract defines no {@code ErrorCode} for them. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
			HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

		if (statusCode.is5xxServerError()) {
			return super.handleExceptionInternal(exception, body, headers, statusCode, request);
		}

		ApiError error = ApiErrors.of(statusCode, codeFor(statusCode), messageFor(statusCode),
				pathOf(request));

		HttpHeaders jsonHeaders = new HttpHeaders();
		jsonHeaders.addAll(headers == null ? new HttpHeaders() : headers);
		jsonHeaders.setContentType(MediaType.APPLICATION_JSON);

		return new ResponseEntity<>(error, jsonHeaders, statusCode);
	}

	private static ErrorCode codeFor(HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
			return ErrorCode.UNAUTHENTICATED;
		}
		if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
			return ErrorCode.FORBIDDEN_SCOPE;
		}
		if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			return ErrorCode.NOT_FOUND;
		}
		// Every remaining 4xx is the caller sending something the endpoint cannot accept.
		return ErrorCode.VALIDATION_FAILED;
	}

	private static String messageFor(HttpStatusCode statusCode) {
		return switch (codeFor(statusCode)) {
			case UNAUTHENTICATED -> ApiErrors.UNAUTHENTICATED_MESSAGE;
			case FORBIDDEN_SCOPE -> ApiErrors.FORBIDDEN_MESSAGE;
			case NOT_FOUND -> "Not found.";
			default -> ApiErrors.VALIDATION_FAILED_MESSAGE;
		};
	}

	private static String pathOf(WebRequest request) {
		return request instanceof ServletWebRequest servletRequest
				? servletRequest.getRequest().getRequestURI()
				: request.getDescription(false);
	}

	private static ResponseEntity<ApiError> json(ApiError error) {
		return ResponseEntity.status(error.status()).contentType(MediaType.APPLICATION_JSON).body(error);
	}

}
