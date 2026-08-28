package com.tf.reader.ingest.api;

import java.time.Duration;

/**
 * Puts and gets whole ingest-related objects - raw uploads, final (possibly encrypted) content,
 * encrypted search indexes - by a deterministic key. Backblaze B2 today, via its S3-compatible
 * API, behind this seam so a future swap (a different provider, or local disk for a dev profile)
 * does not ripple into ingest's orchestration or the real content-serving path.
 */
public interface BookStorage {

	void store(String key, byte[] data, String contentType);

	byte[] load(String key);

	/** The Content-Type declared when {@code key} was stored - the real client-declared upload
	 * type, not a guess derived from format alone. */
	String contentType(String key);

	void delete(String key);

	/**
	 * A time-limited URL a device can fetch {@code key} from directly, without ever holding our
	 * storage credentials. The bucket is private, so this is the only way anything outside this
	 * server ever reads an object.
	 */
	PresignedObject presign(String key, Duration ttl);

}
