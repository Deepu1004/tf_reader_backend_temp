package com.tf.reader.library.support;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.tf.reader.auth.api.SessionQuery;
import com.tf.reader.auth.api.SessionView;
import com.tf.reader.common.error.ApiException;

/**
 * Turns the request's authenticated identity into this module's {@link ReaderIdentity}.
 *
 * <p><b>It does not read claims.</b> The auth module's {@code CurrentUserJwtConverter} is the only
 * place in the backend that does, and it runs after signature verification and validation — so by
 * the time a request reaches a library endpoint the identity is already decided. Re-reading the
 * token here would be a second interpretation of the same claims, free to disagree with the first.
 *
 * <p>Delegates entirely to {@code auth.api.SessionQuery}, the published seam - now that it has an
 * implementation, this module has no reason left to import {@code auth.model} directly.
 */
@Component
public class CurrentReaderResolver {

	private final SessionQuery session;

	public CurrentReaderResolver(SessionQuery session) {
		this.session = session;
	}

	/**
	 * @throws ApiException 401 if the request carries no verified identity. Deny by default: a
	 *                      library endpoint that falls back to any other source of a userId is one
	 *                      that can be asked for somebody else's shelf
	 */
	public ReaderIdentity require(Authentication authentication) {
		SessionView view = session.of(authentication);
		return new ReaderIdentity(view.userId(), view.institutionId());
	}

}
