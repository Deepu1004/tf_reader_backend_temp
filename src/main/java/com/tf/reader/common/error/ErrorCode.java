package com.tf.reader.common.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
	TOO_MANY_IDS(HttpStatus.BAD_REQUEST),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN_SCOPE(HttpStatus.FORBIDDEN),
	FORBIDDEN_INSTITUTION_MISMATCH(HttpStatus.FORBIDDEN),
	NO_ENTITLEMENT(HttpStatus.FORBIDDEN),
	DOWNLOAD_NOT_PERMITTED(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	CODE_TAKEN(HttpStatus.CONFLICT),
	CONTENT_NOT_READY(HttpStatus.CONFLICT),
	STALE_VERSION(HttpStatus.CONFLICT),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

}
