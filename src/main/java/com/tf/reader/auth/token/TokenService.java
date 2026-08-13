package com.tf.reader.auth.token;

import com.tf.reader.auth.model.TnfUser;

/**
 * Mints the application's access token for a user who has <b>already</b> been authenticated.
 *
 * <p>The single argument is deliberate and is the whole security property of this interface.
 * A {@link TnfUser} can only be produced by the mapping stage, from an assertion Spring
 * Security validated and an institution our own backend chose. There is no overload taking an
 * email, a userId, a role list or an institutionId, so there is no path by which a caller -
 * including a request body - can influence what the token says.
 *
 * <p>This interface performs no authentication, finds no users and resolves no institutions.
 * By the time it is called, all of that has happened.
 */
public interface TokenService {

	/**
	 * Issues an access token for an authenticated user.
	 *
	 * @param user the authenticated user; every claim in the token comes from this object
	 */
	IssuedToken issue(TnfUser user);
}
