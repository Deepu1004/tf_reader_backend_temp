package com.tf.reader.common.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.ErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders every authentication failure as a 401 in the shared error envelope, so a request rejected
 * inside the filter chain looks the same to a client as one rejected inside a controller.
 *
 * <p>The message is intentionally uniform. Whether the token was malformed, expired, signed with the
 * wrong key, issued for another audience or tied to a revoked session, the client is told only that
 * authentication failed, so the response cannot be used to probe token or account state.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ErrorResponseWriter errorResponseWriter;

	public ProblemAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
		this.errorResponseWriter = errorResponseWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authenticationException) throws IOException {

		this.errorResponseWriter.write(request, response, ErrorCode.UNAUTHENTICATED,
				"A valid token is required.");
	}

}
