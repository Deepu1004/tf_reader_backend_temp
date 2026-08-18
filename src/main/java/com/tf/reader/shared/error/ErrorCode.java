package com.tf.reader.shared.error;

import org.springframework.http.HttpStatus;

/**
 * The stable enum the app branches on. See the API Reference, section 2.
 *
 * <p>Only the codes that are actually raised today are listed. Codes are added as the
 * endpoints that raise them are built, because the Definition of Done requires every code
 * to have a test that triggers it.
 */
public enum ErrorCode {

	/**
	 * 400. A malformed or incomplete request body. The API Reference defines status 400 as
	 * "Malformed request" but does not name a code for it; this name is ours and needs
	 * ratifying at the Contracts Gate.
	 */
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

	/** 401. No credential was presented on a route that requires one. */
	TOKEN_MISSING(HttpStatus.UNAUTHORIZED),

	/** 401. A token was presented and it is past its expiry. The app signs in again. */
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),

	/**
	 * 401. A token was presented and is not usable: bad signature, malformed, or missing a
	 * claim we require. Deliberately one code for all three - which part of a rejected token
	 * failed is useful to somebody probing us and to nobody else.
	 *
	 * <p>Ours, not in the API Reference table. Raise at the Contracts Gate.
	 */
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED),

	/**
	 * 401. The SAML response did not validate, or the sign-in transaction it referred to was
	 * unknown, already used or expired. Deliberately one code for all of those: telling a
	 * caller which part of a failed sign-in failed only helps an attacker.
	 *
	 * <p>Ours, not in the API Reference table. Raise at the Contracts Gate.
	 */
	SAML_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED),

	/**
	 * 403. The SAML assertion was valid, but that identity holds no membership at the
	 * institution the sign-in was started for. Authenticated is not the same as provisioned.
	 *
	 * <p>Ours, not in the API Reference table. Raise at the Contracts Gate.
	 */
	USER_NOT_PROVISIONED(HttpStatus.FORBIDDEN),

	/**
	 * 403. The resource belongs to another institution.
	 *
	 * <p>This code means <b>only</b> that. The API Reference currently also specifies it as the
	 * 403 for a non-admin caller on {@code /admin/reconcile}, which is a role failure and not an
	 * institution mismatch - {@link #FORBIDDEN_ROLE} is for that. Overloading one code means the
	 * app cannot tell "you are in the wrong tenant" from "you lack a role", and those need
	 * different messages.
	 */
	WRONG_INSTITUTION(HttpStatus.FORBIDDEN),

	/**
	 * 403. The caller is authenticated but does not hold a role this operation requires.
	 *
	 * <p>Ours, and the name the API Reference's own open item proposes. Still unratified.
	 */
	FORBIDDEN_ROLE(HttpStatus.FORBIDDEN),

	/** 404. Unknown id, or not visible to this user. */
	NOT_FOUND(HttpStatus.NOT_FOUND),

	/**
	 * 403. The user holds no entitlement for this title — not subscribed, or entitlement expired.
	 * Loan module raises this when {@code EntitlementQuery.check} returns {@code entitled=false}.
	 */
	NO_ENTITLEMENT(HttpStatus.FORBIDDEN),

	/**
	 * 409. A borrow was attempted for an Elite title but no slot is currently free.
	 * The caller should direct the user to {@code /api/v1/holds} to join the wait queue.
	 */
	NO_COPIES_AVAILABLE(HttpStatus.CONFLICT),

	/**
	 * 409. A return was attempted but the loan is not ACTIVE (already RETURNED or EXPIRED).
	 * Safe to retry on the client side once the caller has refreshed the loan state.
	 */
	LOAN_NOT_ACTIVE(HttpStatus.CONFLICT),

	/** 500. An unexpected internal failure. The traceId in the body links to the server log. */
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
