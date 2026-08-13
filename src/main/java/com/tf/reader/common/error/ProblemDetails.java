package com.tf.reader.common.error;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 9457 responses used across the API.
 *
 * <p>Shared by the {@code @RestControllerAdvice} and by the security filter-chain handlers so that
 * an error raised inside a controller and one raised before routing look identical to a client.
 */
public final class ProblemDetails {

	public static final URI UNAUTHORIZED_TYPE = URI.create("https://api.tf-reader/problems/unauthorized");
	public static final URI FORBIDDEN_TYPE = URI.create("https://api.tf-reader/problems/forbidden");
	public static final URI VALIDATION_TYPE = URI.create("https://api.tf-reader/problems/validation-failed");

	private ProblemDetails() {
	}

	public static ProblemDetail of(HttpStatus status, URI type, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(type);
		problem.setTitle(title);
		problem.setProperty("timestamp", Instant.now().toString());
		return problem;
	}

	public static ProblemDetail unauthorized(String detail) {
		return of(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_TYPE, "Unauthorized", detail);
	}

	public static ProblemDetail forbidden(String detail) {
		return of(HttpStatus.FORBIDDEN, FORBIDDEN_TYPE, "Forbidden", detail);
	}

}
