package com.tf.reader.common.error;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Serialises a {@link ProblemDetail} straight onto the servlet response.
 *
 * <p>Needed because the security filter chain rejects requests before Spring MVC's exception
 * handling is reachable.
 */
@Component
public class ProblemDetailWriter {

	private final ObjectMapper objectMapper;

	public ProblemDetailWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
			throws IOException {

		if (response.isCommitted()) {
			return;
		}
		problem.setInstance(java.net.URI.create(request.getRequestURI()));

		response.setStatus(problem.getStatus());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.objectMapper.writeValue(response.getWriter(), problem);
	}

}
