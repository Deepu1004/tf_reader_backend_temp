package com.tf.reader.library.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.library.entity.OutboxEntry;

/**
 * The {@code changeLogOutbox} collection — failed {@code ChangeLog.record()} calls, waiting for
 * {@code OutboxReplayService} to retry them.
 */
public interface ChangeLogOutboxRepository extends MongoRepository<OutboxEntry, String> {

	/**
	 * Oldest failure first, so a jammed entry does not starve the ones behind it forever, and
	 * bounded, so one tick cannot try to drain an unbounded backlog.
	 *
	 * <p>The bound matters because of when this collection fills: entries accumulate only while
	 * something is already broken, so the first tick after a long outage is the one that would
	 * otherwise read everything at once, against the database that has just come back.
	 */
	List<OutboxEntry> findAllByOrderByFailedAtAsc(Limit limit);
}
