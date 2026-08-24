package com.tf.reader.library.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tf.reader.library.dto.LibraryHold;
import com.tf.reader.library.dto.LibraryLoan;
import com.tf.reader.library.dto.LibraryResponse;
import com.tf.reader.library.repository.MockLibraryRepository;
import com.tf.reader.library.support.ReaderIdentity;
import com.tf.reader.loan.api.ActiveLoanQuery;

/**
 * Builds the library response from loans, holds and the change feed.
 *
 * <p><b>Loans are real; holds are still seeded.</b> {@code loans} come from the published
 * {@code ActiveLoanQuery} seam, which gained {@code findAllFor(userId)} for exactly this screen
 * (D-025). {@code holds} come from {@link MockLibraryRepository} because
 * {@code HoldSnapshotQueryImpl} is still an empty shell with no bean — see {@link #holdsFor}.
 *
 * <p>The response shape is the same either way, which is what let the screen be built once rather
 * than twice while the seams landed at different times.
 */
@Service
public class LibraryAssembler {

	/** Every row {@code ActiveLoanQuery} returns is live by definition, so the wire status is fixed. */
	private static final String ACTIVE = "ACTIVE";

	private final ChangeFeedService changeFeed;
	private final ActiveLoanQuery activeLoans;
	private final MockLibraryRepository shelves;
	private final Clock clock;

	public LibraryAssembler(ChangeFeedService changeFeed, ActiveLoanQuery activeLoans,
			MockLibraryRepository shelves, Clock clock) {
		this.changeFeed = changeFeed;
		this.activeLoans = activeLoans;
		this.shelves = shelves;
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
	 * The reader's active loans, from the published loan seam.
	 *
	 * <p>{@code findAllFor} applies the D-006 liveness rule — an {@code ACTIVE} row already past its
	 * {@code dueAt} is excluded — so the shelf never shows a loan the reader has effectively lost,
	 * even in the window before the expiry sweep runs.
	 *
	 * <p><b>{@code status} is hard-coded rather than read.</b> {@code ActiveLoanView} carries no
	 * status field, and it does not need one: every row this seam returns is live by definition.
	 *
	 * <p><b>{@code borrowedAt} has no source, so it is omitted.</b> {@code ActiveLoanView} does not
	 * publish it, and inventing a timestamp for a field the app may render is worse than leaving it
	 * out. The frozen {@code LibraryResponse} shape has the field, so this is a gap to close with the
	 * loan lane — either add it to the view, or drop it from the response.
	 */
	private List<LibraryLoan> loansFor(ReaderIdentity reader) {
		return activeLoans.findAllFor(reader.userId()).stream()
				.map(loan -> new LibraryLoan(
						loan.loanId(),
						loan.itemId(),
						loan.licenceModel(),
						ACTIVE,
						null,
						loan.dueAt(),
						loan.canPersist()))
				.toList();
	}

	/**
	 * The reader's holds, offered ones included.
	 *
	 * <p><b>Seeded, because the port has no bean.</b> {@code hold.api.HoldSnapshotQuery} is published
	 * and real, but {@code HoldSnapshotQueryImpl} is an empty shell that neither implements it nor
	 * carries {@code @Service} — injecting the port would fail context startup rather than return an
	 * empty list.
	 *
	 * <p>When it lands, note that {@code position} and {@code queueLength} are computed there on
	 * read — this assembler must not cache them, because a hold ahead of this one cancelling changes
	 * both. Do not write a local {@code @Service} implementing that port either: the moment the hold
	 * lane annotates theirs, the context has two candidates and fails for whoever merges second.
	 * {@link MockLibraryRepository} is a plain component for exactly that reason.
	 */
	private List<LibraryHold> holdsFor(ReaderIdentity reader) {
		return shelves.holdsFor(reader.userId());
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
