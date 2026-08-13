package com.tf.reader.common.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ApiErrorWriter;
import com.tf.reader.common.error.ApiErrors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders every authentication failure as the contract's 401 {@code UNAUTHENTICATED} error, with one
 * uniform message so the response cannot be used to probe token or account state.
 */
@Component
public class ApiErrorAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ApiErrorWriter apiErrorWriter;

	public ApiErrorAuthenticationEntryPoint(ApiErrorWriter apiErrorWriter) {
		this.apiErrorWriter = apiErrorWriter;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authenticationException) throws IOException {

		this.apiErrorWriter.write(request, response, ApiErrors.unauthenticated(request.getRequestURI()));
	}

}
