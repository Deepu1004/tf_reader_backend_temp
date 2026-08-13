package com.tf.reader.common.error;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.getCode();
		return ResponseEntity.status(code.getStatus())
				.body(ErrorResponse.of(code, ex.getMessage(), request.getRequestURI(), newTraceId()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
				.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, ex.getMessage(), request.getRequestURI(), newTraceId()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
				.body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "Request body is malformed.", request.getRequestURI(), newTraceId()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		String traceId = newTraceId();
		log.error("Unhandled exception, traceId={}", traceId, ex);
		ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"INTERNAL_ERROR", "An unexpected error occurred.", request.getRequestURI(), traceId);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	private String newTraceId() {
		return Long.toHexString(ThreadLocalRandom.current().nextLong());
	}

}
