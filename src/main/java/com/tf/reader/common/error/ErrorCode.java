package com.tf.reader.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The machine-readable error vocabulary from the published API contract. Deliberately has no code
 * separating an expired token from a forged one, which would tell an attacker they guessed a real one.
 */
@Schema(name = "ErrorCode", description = "Machine readable error code.")
public enum ErrorCode {

	UNAUTHENTICATED,
	FORBIDDEN_SCOPE,
	FORBIDDEN_INSTITUTION_MISMATCH,
	NO_ENTITLEMENT,
	CONTENT_NOT_READY,
	DOWNLOAD_NOT_PERMITTED,
	NOT_FOUND,
	CODE_TAKEN,
	TOO_MANY_IDS,
	VALIDATION_FAILED,
	STALE_VERSION

}
