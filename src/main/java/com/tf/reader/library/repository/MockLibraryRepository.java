package com.tf.reader.library.repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tf.reader.library.dto.LibraryHold;
import com.tf.reader.library.dto.LibraryOffer;

/**
 * A seeded hold shelf, so the library screen has hold cards to render before the hold seam
 * publishes a list.
 *
 * <p><b>Holds only.</b> Loans used to be seeded here too; they now come from the real
 * {@code loan.api.ActiveLoanQuery}, which gained {@code findAllFor(userId)} for this screen (D-025).
 *
 * <p><b>This is a fixture, not a read model.</b> Nothing here reaches Mongo or Redis, so a hold
 * placed through {@code POST /api/v1/holds} does not appear on these shelves and a loan returned
 * through the loan module does not leave them. The shapes are real; the data is not.
 *
 * <p><b>It lives in {@code library/} on purpose.</b> The alternative — a local {@code @Service}
 * implementing {@code hold.api.HoldSnapshotQuery} — puts a second candidate in the context the
 * moment the hold lane annotates theirs, and the application then fails to start for whoever merges
 * second. A plain component nobody else declares cannot collide.
 *
 * <p>Deleting it is one commit: drop this file and have {@code LibraryAssembler} call the published
 * ports instead.
 *
 * <p>Item ids are the ones in {@code seed/demo-dataset.json}, so
 * {@code POST /api/v1/catalogue/items:batch} resolves them to real titles and covers rather than
 * 404ing on an id invented here.
 */
@Component
public class MockLibraryRepository {

	/** The default {@code POST /api/v1/auth/dev-token} mints, so a token with no parameters lands on a full shelf. */
	private static final String DEV_READER = "usr_dev123";

	private final Clock clock;

	public MockLibraryRepository(Clock clock) {
		this.clock = clock;
	}

	/**
	 * One QUEUED hold and one OFFERED hold, because the card is a different card in each state:
	 * QUEUED shows a queue position and a guess, OFFERED shows a real deadline and no guess.
	 *
	 * <p><b>{@code QUEUED}, not {@code WAITING}.</b> The contract enum is {@code [QUEUED, OFFERED]}
	 * and so is {@code hold.entity.HoldStatus} — a status invented here is one team1 would branch on
	 * and the real hold module would never send.
	 */
	public List<LibraryHold> holdsFor(String userId) {
		Instant now = now();
		return switch (userId) {
			case DEV_READER -> List.of(
					new LibraryHold("hold_mock_q7", "item_q7", "QUEUED", 3, 7, 12,
							now.minus(5, ChronoUnit.DAYS), null),
					// estimatedWaitDays is null once OFFERED — there is a real deadline instead, and
					// showing a guess beside a fact on one card is what confuses a reader into
					// abandoning a copy that is still theirs.
					//
					// position is 1, not 0: the contract has it one-based with minimum 1, and the
					// person holding an offer is by definition at the front of the queue.
					new LibraryHold("hold_mock_f3", "item_f3", "OFFERED", 1, 4, null,
							now.minus(11, ChronoUnit.DAYS),
							new LibraryOffer("offer_mock_f3", now.plus(36, ChronoUnit.HOURS))));
			default -> List.of();
		};
	}

	/**
	 * Whole seconds, and from the injected clock rather than {@code Instant.now()}, so these
	 * timestamps sit on the same clock as the response's {@code serverTime} and a test can move both
	 * together. Relative to now rather than written down as literals: a fixed {@code dueAt} is in the
	 * past by next week and every seeded loan renders as expired.
	 */
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.SECONDS);
	}

}
