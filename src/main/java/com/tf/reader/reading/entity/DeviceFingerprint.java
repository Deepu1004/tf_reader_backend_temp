package com.tf.reader.reading.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The devices a reader is using, one document per reader.
 *
 * <p><b>Why one document holding an array, rather than one document per device.</b> The cap is
 * "how many does this reader have", and an array length is checkable inside a single atomic update.
 * Counting documents would need a read first, and read-then-write is exactly the race the cap has
 * to survive — three devices arriving together would all see room and all be admitted.
 *
 * <p>Nothing here is enrolled. A device is <em>observed</em> from the fingerprint of the key that
 * already arrives on every read, so there is no registration step to build or to clean up.
 */
@Document(collection = "devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceFingerprint {

	@Id
	private String id;

	@Indexed(unique = true)
	private String userId;

	private List<Device> devices = new ArrayList<>();

	private Instant createdAt;
	private Instant updatedAt;

	/**
	 * One observed device.
	 *
	 * <p><b>The fingerprint, never the key.</b> Nothing in this collection should be capable of
	 * impersonating a device, and a public key plus a signature is most of the way there.
	 *
	 * <p>{@code lastSeenAt} exists for the prune job, not for the cap. The cap counts entries; the
	 * prune job removes ones nobody has used for months. Keeping those two concerns apart is what
	 * makes the cap deterministic.
	 */
	public record Device(String fingerprint, Instant firstSeenAt, Instant lastSeenAt) {
	}
}
