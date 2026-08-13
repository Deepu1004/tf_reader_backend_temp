package com.tf.reader.common.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ProblemDetailWriter;
import com.tf.reader.common.error.ProblemDetails;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders authorization failures for authenticated callers as a 403 RFC 9457 problem.
 *
 * <p>The detail never names the scope or role that would have been required, so a denial does not
 * disclose the tenant layout.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

	private final ProblemDetailWriter problemDetailWriter;

	public ProblemAccessDeniedHandler(ProblemDetailWriter problemDetailWriter) {
		this.problemDetailWriter = problemDetailWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		this.problemDetailWriter.write(request, response,
				ProblemDetails.forbidden("You do not have permission to access this resource."));
	}

}
