package com.tf.reader.common.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ProblemDetailWriter;
import com.tf.reader.common.error.ProblemDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders every authentication failure as a 401 RFC 9457 problem.
 *
 * <p>The detail is intentionally uniform. Whether the token was malformed, expired, signed with the
 * wrong key, issued for another audience or tied to a revoked session, the client is told only that
 * authentication failed, so the response cannot be used to probe token or account state.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ProblemDetailWriter problemDetailWriter;

	public ProblemAuthenticationEntryPoint(ProblemDetailWriter problemDetailWriter) {
		this.problemDetailWriter = problemDetailWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authenticationException) throws IOException {

		this.problemDetailWriter.write(request, response,
				ProblemDetails.unauthorized("Authentication is required to access this resource."));
	}

}
