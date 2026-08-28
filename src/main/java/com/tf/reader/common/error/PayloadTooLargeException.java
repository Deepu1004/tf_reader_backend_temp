package com.tf.reader.common.error;

/**
 * A file exceeded the applicable ingest size cap: 100 MB in general, 25 MB for anything that will
 * be locked (a locked file is decrypted whole on the device, so it cannot be too large to hold in
 * memory there). Mapped to 413 carrying {@code VALIDATION_FAILED} - the framework-explicit-status
 * overload on {@link ErrorResponse} exists for exactly this case.
 */
public class PayloadTooLargeException extends RuntimeException {

	public PayloadTooLargeException(String message) {
		super(message);
	}

}
