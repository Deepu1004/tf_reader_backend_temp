package com.tf.reader.auth.saml;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorResponse;
import com.tf.reader.common.error.TraceIds;

import tools.jackson.databind.json.JsonMapper;

/**
 * Runs at the ACS once Spring Security has validated the SAML response.
 *
 * <p>HTTP only: it reads the RelayState parameter, hands it to
 * {@link SamlAuthenticationService} with the validated authentication, and serialises whatever
 * comes back. All of the deciding lives in the service, so none of it needs a servlet to test.
 *
 * <p>When TokenService arrives, the body written here becomes the token envelope. Nothing else
 * about this class changes.
 */
@Component
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	/** The parameter the IdP echoes our transaction id back in. */
	private static final String RELAY_STATE = "RelayState";

	private final SamlAuthenticationService authenticationService;
	private final JsonMapper jsonMapper;

	public SamlAuthenticationSuccessHandler(SamlAuthenticationService authenticationService,
			JsonMapper jsonMapper) {
		this.authenticationService = authenticationService;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		try {
			Object result =
					authenticationService.complete(authentication, request.getParameter(RELAY_STATE));
			writeBody(response, HttpServletResponse.SC_OK, result);
		}
		catch (ApiException failure) {
			// A valid assertion can still fail to become a sign-in - an expired transaction, or
			// an identity with no membership at the institution it was started for.
			writeBody(response, failure.getCode().getStatus().value(),
					ErrorResponse.of(failure.getCode(), failure.getMessage(), request.getRequestURI(),
							TraceIds.newTraceId()));
		}
		finally {
			discardTheSignInSession(request);
		}
	}

	/**
	 * Ends the session the SAML leg needed, now that it has done its one job.
	 *
	 * <p>The session exists so the ACS can check {@code InResponseTo} against the AuthnRequest we
	 * sent. By this line that check has happened - but Spring Security has also <b>persisted the
	 * SAML authentication into that session</b>, before this handler was called. Left alive, the
	 * JSESSIONID is a second credential for an identity that never passed through the JWT
	 * validator, and one this application cannot expire, revoke or reason about: the token design
	 * is a one-hour idle timeout on a bearer token, not a server-side session.
	 *
	 * <p>In a {@code finally} because the refusal path above is reached <em>after</em> that same
	 * persistence, so a sign-in we rejected must not leave an authenticated session behind either.
	 */
	private void discardTheSignInSession(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
	}

	private void writeBody(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(jsonMapper.writeValueAsString(body));
	}
}
