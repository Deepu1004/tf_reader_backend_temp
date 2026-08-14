package com.tf.reader.common.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.ErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders authorization failures for authenticated callers as a 403 in the shared error envelope.
 *
 * <p>The message never names the scope or role that would have been required, so a denial does not
 * disclose the tenant layout. {@code FORBIDDEN_SCOPE} matches what {@code GlobalExceptionHandler}
 * returns for a denial raised inside a controller.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

	private final ErrorResponseWriter errorResponseWriter;

	public ProblemAccessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		this.errorResponseWriter.write(request, response, ErrorCode.FORBIDDEN_SCOPE,
				"You do not have permission to perform this action.");
	}

}
