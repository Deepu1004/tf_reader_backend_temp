package com.tf.reader.auth.api;

import org.springframework.security.core.Authentication;

/**
 * Published contract: who is the caller of this request?
 *
 * <p>PROPOSED by Deepak (reading), as the consumer. Takes the authenticated principal as
 * a parameter and never reads the security context itself, so the same call works whether
 * it is made from a controller or, later, from a scheduled thread with an explicit
 * principal — the reconciler and the sweeps have no request to read one from.
 */
public interface SessionQuery {

	SessionView of(Authentication authentication);
}
