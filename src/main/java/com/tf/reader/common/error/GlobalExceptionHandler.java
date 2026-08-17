package com.tf.reader.common.error;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

// @RestControllerAdvice disabled — shared.error.GlobalExceptionHandler is the canonical one (D-019).
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.getCode();
		return respond(code, ex.getMessage(), request, ex);
	}


	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		return respond(ErrorCode.FORBIDDEN_SCOPE, "You do not have permission to perform this action.", request, ex);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		String message = ex.getConstraintViolations().stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.collect(Collectors.joining(", "));
		return respond(ErrorCode.VALIDATION_FAILED, message, request, ex);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request, ex);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ErrorCode code;
		String message;

		if (ex instanceof MethodArgumentNotValidException validationEx) {
			code = ErrorCode.VALIDATION_FAILED;
			message = fieldErrorsMessage(validationEx);
		} else if (ex instanceof HttpMessageNotReadableException) {
			code = ErrorCode.VALIDATION_FAILED;
			message = "Request body is malformed.";
		} else if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			code = ErrorCode.NOT_FOUND;
			message = "No such resource.";
		} else if (statusCode.is4xxClientError()) {
			code = ErrorCode.VALIDATION_FAILED;
			message = "The request could not be processed.";
		} else {
			code = ErrorCode.INTERNAL_ERROR;
			message = "An unexpected error occurred.";
		}

		String traceId = newTraceId();
		String path = path(request);
		logFailure(traceId, statusCode.value(), code, path, ex);
		return ResponseEntity.status(statusCode)
				.body(ErrorResponse.of(statusCode.value(), code, message, path, traceId));
	}

	private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request,
			Exception ex) {

		String traceId = newTraceId();
		String path = request.getRequestURI();
		logFailure(traceId, code.getStatus().value(), code, path, ex);
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, message, path, traceId));
	}


	private void logFailure(String traceId, int status, ErrorCode code, String path, Exception ex) {
		if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
			log.error("Request failed, traceId={} status={} code={} path={}", traceId, status, code, path, ex);
		}
		else {
			log.info("Request failed, traceId={} status={} code={} path={}", traceId, status, code, path);
		}
	}

	private String fieldErrorsMessage(MethodArgumentNotValidException ex) {
		return ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.collect(Collectors.joining(", "));
	}

	private String path(WebRequest request) {
		return ((ServletWebRequest) request).getRequest().getRequestURI();
	}

	private String newTraceId() {
		return Long.toHexString(ThreadLocalRandom.current().nextLong());
	}

}
