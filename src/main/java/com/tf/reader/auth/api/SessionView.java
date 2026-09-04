package com.tf.reader.auth.api;

import java.util.List;

import com.tf.reader.catalogue.api.SubjectRef;

/**
 * Read model describing the current session.
 *
 * <p>Originally proposed by Deepak (reading) as the consumer — the read broker's controller needs
 * exactly this shape to build a {@link SubjectRef} without importing {@code auth.model}. Now also
 * consumed by {@code library.support.CurrentReaderResolver}. Change it if it is wrong for another
 * lane's use.
 *
 * <p>{@code institutionId} is null for an individual subscriber. That is not the same as
 * belonging to every institution, so every institution-scoped rule must treat it as
 * belonging to none.
 */
public record SessionView(
		String userId,
		String institutionId,
		List<String> roles) {

	/** The shared identity type for a cross-capability call. */
	public SubjectRef toSubjectRef() {
		return new SubjectRef(userId, institutionId);
	}
}
