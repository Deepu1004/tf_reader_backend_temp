package com.tf.reader.library.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tf.reader.library.dto.LibraryHold;
import com.tf.reader.library.dto.LibraryLoan;
import com.tf.reader.library.dto.LibraryResponse;
import com.tf.reader.library.support.ReaderIdentity;

/**
 * Builds the library response from loans, holds and the change feed.
 *
 * <p><b>Currently a read model over one source.</b> The cursor and {@code serverTime} are real;
 * {@code loans} and {@code holds} are empty arrays. That is forced rather than deferred — see
 * {@link #loansFor} and {@link #holdsFor} — and the response shape is what unblocks the app either
 * way, so the screen gets built once instead of twice.
 */
@Service
public class LibraryAssembler {

	private final ChangeFeedService changeFeed;
	private final Clock clock;

	public LibraryAssembler(ChangeFeedService changeFeed, Clock clock) {
		this.changeFeed = changeFeed;
		this.clock = clock;
	}

	public LibraryResponse assemble(ReaderIdentity reader) {
		// Cursor first, loans and holds second, and the order is the point. Read the cursor last and
		// a change landing in between is behind the cursor but missing from the snapshot, so the app
		// never learns about it — a revocation lost permanently, with the device keeping the key.
		// Read it first and that same change is merely replayed on the next poll, which is harmless:
		// applying these transitions twice converges on the same shelf.
		//
		// Week-2 task 9 says the opposite. §11's own definition of done — "the cursor is never ahead
		// of the data beside it" — agrees with this ordering. Raised at the gate.
		ChangeCursor cursor = changeFeed.currentCursor(reader.userId());

		List<LibraryLoan> loans = loansFor(reader);
		List<LibraryHold> holds = holdsFor(reader);

		return new LibraryResponse(loans, holds, cursor.value(), serverTime());
	}

	/**
	 * The reader's active loans.
	 *
	 * <p><b>Empty because it has to be.</b> {@code loan.api.ActiveLoanQuery} is an empty interface
	 * and {@code ActiveLoanQueryImpl} carries no {@code @Service}, so there is no bean —
	 * constructor-injecting it would fail context startup rather than return an empty list.
	 *
	 * <p>When it lands, this maps its view onto {@link LibraryLoan} and is the only place the loan
	 * enums become wire strings.
	 */
	private List<LibraryLoan> loansFor(ReaderIdentity reader) {
		return List.of();
	}

	/**
	 * The reader's holds, offered ones included.
	 *
	 * <p>Empty for the same reason: {@code hold.api.HoldSnapshotQuery} is published and real, but
	 * {@code HoldSnapshotQueryImpl} is an empty shell with no {@code @Service}.
	 *
	 * <p>When it lands, note that {@code position} and {@code queueLength} are computed there on
	 * read — this assembler must not cache them, because a hold ahead of this one cancelling changes
	 * both. Do not write a local {@code @Service} implementing that port either: the moment the hold
	 * lane annotates theirs, the context has two candidates and fails for whoever merges second.
	 */
	private List<LibraryHold> holdsFor(ReaderIdentity reader) {
		return List.of();
	}

	/**
	 * The one clock on this screen.
	 *
	 * <p>Whole seconds per the wire convention, and from the injected clock rather than
	 * {@code Instant.now()} — every countdown the app renders is a difference against this value, so
	 * a test has to be able to move it.
	 */
	private Instant serverTime() {
		return clock.instant().truncatedTo(ChronoUnit.SECONDS);
	}

}
