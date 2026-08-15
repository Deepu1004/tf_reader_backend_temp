package com.tf.reader.common.error;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes an {@link ErrorResponse} straight onto the servlet response.
 *
 * <p>Needed because the security filter chain rejects a request before Spring MVC's exception
 * handling can run, so {@code @RestControllerAdvice} never sees a missing or invalid token — the most
 * common failure in the system. Without this, those responses would carry the framework's default
 * body and clients would face two error shapes depending on where the request died.
 */
@Slf4j
@Component
public class ErrorResponseWriter {

	private final ObjectMapper objectMapper;

	public ErrorResponseWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * @param code    the code returned to the client; its status is the response status
	 * @param message safe to disclose, so it must not name what was missing or why
	 */
	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
			throws IOException {

		if (response.isCommitted()) {
			return;
		}

		String traceId = TraceIds.newTraceId();
		String path = request.getRequestURI();
		log.info("Request rejected in the security chain, traceId={} status={} code={} path={}", traceId,
				code.getStatus().value(), code, path);

		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		this.objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message, path, traceId));
	}

}
