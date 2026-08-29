package com.tf.reader.ingest.api;

/**
 * {@code key} does not exist in storage. Provider-agnostic on purpose - {@link BookStorage}
 * exists so {@code IngestProcessor} never has to know it is talking to B2/S3 today, so its
 * implementations translate a provider-specific "not found" into this rather than letting one
 * leak through the seam.
 */
public class ObjectNotFoundException extends RuntimeException {

	public ObjectNotFoundException(String key) {
		super("No object at key: " + key);
	}

}
