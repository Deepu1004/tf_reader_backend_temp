package com.tf.reader.common.error;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Serialises an {@link ApiError} straight onto the servlet response, for the security filter chain,
 * which rejects requests before Spring MVC's exception handling is reachable.
 */
@Component
public class ApiErrorWriter {

	private final ObjectMapper objectMapper;

	public ApiErrorWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ApiError error)
			throws IOException {

		if (response.isCommitted()) {
			return;
		}
		response.setStatus(error.status());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.objectMapper.writeValue(response.getWriter(), error);
	}

}
