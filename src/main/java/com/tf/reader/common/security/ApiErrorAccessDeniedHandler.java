package com.tf.reader.common.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ApiErrorWriter;
import com.tf.reader.common.error.ApiErrors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders authorization failures as the contract's 403 {@code FORBIDDEN_SCOPE} error. The message
 * never names the scope or role that would have been required, so a denial leaks no tenant layout.
 */
@Component
public class ApiErrorAccessDeniedHandler implements AccessDeniedHandler {

	private final ApiErrorWriter apiErrorWriter;

	public ApiErrorAccessDeniedHandler(ApiErrorWriter apiErrorWriter) {
		this.apiErrorWriter = apiErrorWriter;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		this.apiErrorWriter.write(request, response, ApiErrors.forbidden(request.getRequestURI()));
	}

}
