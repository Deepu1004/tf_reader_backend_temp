package com.tf.reader.common.error;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.getCode();
		return ResponseEntity.status(code.getStatus())
				.body(ErrorResponse.of(code, ex.getMessage(), request.getRequestURI(), newTraceId()));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
			HttpServletRequest request) {
		String message = ex.getConstraintViolations().stream()
				.map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
				.collect(Collectors.joining(", "));
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
				.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, message, request.getRequestURI(), newTraceId()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		String traceId = newTraceId();
		log.error("Unhandled exception, traceId={}", traceId, ex);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
				.body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.",
						request.getRequestURI(), traceId));
	}

	/**
	 * Every exception Spring MVC itself raises (bad method, unsupported media type, missing
	 * parameter, unreadable body, no handler, etc.) funnels through here with the correct
	 * status already chosen by the framework - we only translate the body to our envelope.
	 */
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
		if (code == ErrorCode.INTERNAL_ERROR) {
			log.error("Unhandled exception, traceId={}", traceId, ex);
		}
		return ResponseEntity.status(statusCode).body(ErrorResponse.of(code, message, path(request), traceId));
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
