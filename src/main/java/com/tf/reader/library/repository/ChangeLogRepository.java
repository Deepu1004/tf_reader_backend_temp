package com.tf.reader.library.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.tf.reader.library.entity.ChangeLogEntry;

/**
 * Mongo repository for {@link ChangeLogEntry} documents.
 *
 * <p>Both queries are covered by the {@code reader_sequence} index, and both are scoped by
 * {@code userId}. <b>There is deliberately no method here that reads across readers</b> — a change
 * feed that can be asked for somebody else's changes is a data leak with a cursor for a parameter,
 * and the cheapest way to guarantee it cannot happen is to give the query layer no way to say it.
 */
public interface ChangeLogRepository extends MongoRepository<ChangeLogEntry, String> {

	/**
	 * A page of one reader's feed, oldest first, strictly after {@code sequence}.
	 *
	 * <p><b>Strictly after</b>, so a client replaying its stored cursor is not handed back the entry
	 * it has already applied.
	 *
	 * <p>Callers ask for one more than they intend to return. That is how {@code hasMore} is
	 * answered without counting the stream, which would mean reading all of it to answer a question
	 * the client never asks.
	 */
	List<ChangeLogEntry> findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
			String userId, long sequence, Limit limit);

	/**
	 * The reader's newest entry — their high-water mark.
	 *
	 * <p>Two things need it: the cursor handed out beside a library response, and the refusal of a
	 * cursor from the future. Empty for a reader who has never had a change, which reads as sequence
	 * zero rather than as an error.
	 */
	Optional<ChangeLogEntry> findFirstByUserIdOrderBySequenceDesc(String userId);

}
