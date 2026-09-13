package com.tf.reader.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.tf.reader.auth.api.SessionQuery;
import com.tf.reader.auth.api.SessionView;
import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * Implementation of {@link SessionQuery}.
 *
 * <p>The same guard {@code library.support.CurrentReaderResolver} used to run itself, before this
 * existed: authenticated, the principal is a verified {@link CurrentUser} (not merely something
 * else that happened to authenticate), and its {@code userId} is not blank. Anything else is
 * refused rather than mined for an identity.
 */
@Service
public class SessionQueryImpl implements SessionQuery {

	@Override
	public SessionView of(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof CurrentUser reader
				&& reader.userId() != null && !reader.userId().isBlank()) {
			return new SessionView(reader.userId(), reader.institutionId(), reader.roles());
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, "Sign in required.");
	}
}
