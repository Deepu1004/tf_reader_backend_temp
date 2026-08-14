package com.tf.reader.auth.saml;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.tf.reader.shared.error.ApiError;
import com.tf.reader.shared.error.ErrorCode;
import com.tf.reader.shared.error.TraceId;

import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a rejected SAML response into our canonical error body.
 *
 * <p>Spring Security's default would redirect to an error page, which is useless to an API
 * client and hides the refusal behind a 302.
 *
 * <p>The reason is logged but never returned. A caller learns that sign-in failed, not which
 * check failed - "signature did not verify" and "audience did not match" are useful to an
 * attacker probing our configuration and to nobody else.
 */
@Component
public class SamlAuthenticationFailureHandler implements AuthenticationFailureHandler {

	private static final org.slf4j.Logger log =
			org.slf4j.LoggerFactory.getLogger(SamlAuthenticationFailureHandler.class);

	private final JsonMapper jsonMapper;

	public SamlAuthenticationFailureHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		String traceId = TraceId.next();
		log.warn("SAML authentication rejected [traceId={}]: {}", traceId, exception.getMessage());

		ApiError body = ApiError.of(ErrorCode.SAML_AUTHENTICATION_FAILED,
				"The SAML response could not be validated.", traceId);
		response.setStatus(ErrorCode.SAML_AUTHENTICATION_FAILED.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(jsonMapper.writeValueAsString(body));
	}
}
