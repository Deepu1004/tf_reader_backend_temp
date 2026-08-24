package com.tf.reader.library;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.tf.reader.library.dto.LibraryResponse;
import com.tf.reader.library.repository.MockLibraryRepository;
import com.tf.reader.library.service.ChangeCursor;
import com.tf.reader.library.service.ChangeFeedService;
import com.tf.reader.library.service.LibraryAssembler;
import com.tf.reader.library.support.ReaderIdentity;
import com.tf.reader.loan.api.ActiveLoanQuery;
import com.tf.reader.loan.api.ActiveLoanView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryAssemblerTest {

	private static final ReaderIdentity READER = new ReaderIdentity("user_9c2", "inst_7f3");

	/** The one identity {@code MockLibraryRepository} seeds hold cards for. */
	private static final ReaderIdentity SEEDED_READER = new ReaderIdentity("usr_dev123", "inst_7f3");

	private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final ChangeFeedService changeFeed = mock(ChangeFeedService.class);
	private final ActiveLoanQuery activeLoans = mock(ActiveLoanQuery.class);

	// The hold fixture is the real component, not a Mockito mock: it holds no collaborator but the
	// clock, so stubbing it would only assert that the seed says what the seed says.
	private final LibraryAssembler assembler = new LibraryAssembler(
			changeFeed, activeLoans, new MockLibraryRepository(CLOCK), CLOCK);

	@Test
	@DisplayName("loans and holds are empty arrays, never absent, so the screen is built once")
	void publishesTheShapeBeforeTheContent() {
		givenCursor(ChangeCursor.of(1189L));
		givenLoans();

		LibraryResponse response = assembler.assemble(READER);

		// A reader with no loans and no seeded holds is what a brand new reader looks like: empty,
		// never null.
		assertThat(response.loans()).isNotNull().isEmpty();
		assertThat(response.holds()).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("loans come from the published loan seam, mapped onto the wire shape")
	void mapsLoansFromTheSeam() {
		givenCursor(ChangeCursor.of(4L));
		givenLoans(
				new ActiveLoanView("loan_7c1", "item_42", "ELITE", false,
						NOW.plus(13, ChronoUnit.DAYS)),
				new ActiveLoanView("loan_oa9", "item_oa9", "OPEN_ACCESS", true, null));

		LibraryResponse response = assembler.assemble(READER);

		assertThat(response.loans()).extracting("itemId").containsExactly("item_42", "item_oa9");
		assertThat(response.loans().get(0).licenceModel()).isEqualTo("ELITE");
		assertThat(response.loans().get(0).canPersist()).isFalse();
	}

	@Test
	@DisplayName("every loan from this seam is ACTIVE, because the seam only returns live ones")
	void statusIsAlwaysActive() {
		givenCursor(ChangeCursor.of(4L));
		givenLoans(new ActiveLoanView("loan_7c1", "item_42", "ELITE", false,
				NOW.plus(13, ChronoUnit.DAYS)));

		// ActiveLoanView carries no status field and does not need one: findAllFor applies the D-006
		// liveness rule, so a lapsed-but-unswept row never reaches here.
		assertThat(assembler.assemble(READER).loans().get(0).status()).isEqualTo("ACTIVE");
	}

	@Test
	@DisplayName("an open-ended loan carries no dueAt, so the card shows no countdown")
	void openEndedLoanHasNoDueDate() {
		givenCursor(ChangeCursor.of(4L));
		givenLoans(
				new ActiveLoanView("loan_7c1", "item_42", "ELITE", false,
						NOW.plus(13, ChronoUnit.DAYS)),
				new ActiveLoanView("loan_oa9", "item_oa9", "OPEN_ACCESS", true, null));

		LibraryResponse response = assembler.assemble(READER);

		assertThat(response.loans().get(1).dueAt()).isNull();
		// A dated loan has to be ahead of the response's own serverTime, or the app renders a loan
		// that expired before it was handed over.
		assertThat(response.loans().get(0).dueAt()).isAfter(response.serverTime());
	}

	@Test
	@DisplayName("borrowedAt is omitted, because the seam does not publish it")
	void borrowedAtHasNoSourceYet() {
		givenCursor(ChangeCursor.of(4L));
		givenLoans(new ActiveLoanView("loan_7c1", "item_42", "ELITE", false,
				NOW.plus(13, ChronoUnit.DAYS)));

		// Null rather than invented. The frozen response shape has the field, so this is the gap to
		// close with the loan lane — add it to ActiveLoanView, or drop it from LibraryResponse.
		assertThat(assembler.assemble(READER).loans().get(0).borrowedAt()).isNull();
	}

	@Test
	@DisplayName("a seeded reader gets hold cards, so the offer section has something to render")
	void seededReaderGetsHolds() {
		when(changeFeed.currentCursor(SEEDED_READER.userId())).thenReturn(ChangeCursor.of(4L));
		givenLoans();

		LibraryResponse response = assembler.assemble(SEEDED_READER);

		assertThat(response.holds()).extracting("itemId").containsExactly("item_q7", "item_f3");
	}

	@Test
	@DisplayName("an offered hold swaps the wait guess for a real deadline")
	void offeredHoldHasADeadlineAndNoGuess() {
		when(changeFeed.currentCursor(SEEDED_READER.userId())).thenReturn(ChangeCursor.of(4L));
		givenLoans();

		LibraryResponse response = assembler.assemble(SEEDED_READER);

		var offered = response.holds().get(1);
		assertThat(offered.status()).isEqualTo("OFFERED");
		assertThat(offered.estimatedWaitDays()).isNull();
		assertThat(offered.offer().expiresAt()).isAfter(response.serverTime());
		// One-based, per the contract's minimum of 1 — and whoever holds an offer is at the front.
		assertThat(offered.position()).isEqualTo(1);

		var queued = response.holds().get(0);
		assertThat(queued.status()).isEqualTo("QUEUED");
		assertThat(queued.offer()).isNull();
		assertThat(queued.estimatedWaitDays()).isNotNull();
	}

	@Test
	@DisplayName("seeded hold statuses are the contract's, not invented ones")
	void seededStatusesAreInTheContractEnum() {
		when(changeFeed.currentCursor(SEEDED_READER.userId())).thenReturn(ChangeCursor.of(4L));
		givenLoans();

		// The contract enum is [QUEUED, OFFERED] and so is hold.entity.HoldStatus. A status invented
		// in the fixture is one team1 branches on and the real hold module never sends.
		assertThat(assembler.assemble(SEEDED_READER).holds())
				.extracting("status")
				.containsOnly("QUEUED", "OFFERED");
	}

	@Test
	@DisplayName("the cursor is the reader's own feed position, as at this response")
	void carriesTheFeedCursor() {
		givenCursor(ChangeCursor.of(1189L));
		givenLoans();

		assertThat(assembler.assemble(READER).cursor()).isEqualTo("1189");
	}

	@Test
	@DisplayName("a reader with no history gets a cursor they can send straight back")
	void newReaderGetsTheBeginning() {
		givenCursor(ChangeCursor.BEGINNING);
		givenLoans();

		String cursor = assembler.assemble(READER).cursor();

		assertThat(cursor).isEqualTo("0");
		// The app sends this back unmodified on its first sync, so it has to parse.
		assertThat(ChangeCursor.parse(cursor)).isEqualTo(ChangeCursor.BEGINNING);
	}

	@Test
	@DisplayName("serverTime is the server's, so every countdown on the screen has one anchor")
	void anchorsToServerTime() {
		givenCursor(ChangeCursor.of(1189L));
		givenLoans();

		assertThat(assembler.assemble(READER).serverTime()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("the cursor is read before the shelf, so nothing falls between the two reads")
	void readsTheCursorBeforeTheShelf() {
		givenCursor(ChangeCursor.of(1189L));
		givenLoans();

		assembler.assemble(READER);

		// Now assertable, because there is finally a real read to order against. Cursor last would
		// mean a change landing mid-assembly is behind the cursor but missing from the snapshot, and
		// the app never learns about it.
		InOrder order = inOrder(changeFeed, activeLoans);
		order.verify(changeFeed).currentCursor("user_9c2");
		order.verify(activeLoans).findAllFor("user_9c2");
	}

	@Test
	@DisplayName("the loans asked for are this reader's, never anybody else's")
	void asksTheSeamForThisReader() {
		givenCursor(ChangeCursor.of(1189L));
		givenLoans();

		assembler.assemble(READER);

		verify(activeLoans).findAllFor("user_9c2");
	}

	@Test
	@DisplayName("an individual subscriber has no institution and is not defaulted into one")
	void individualSubscriberBelongsToNoInstitution() {
		assertThat(new ReaderIdentity("user_solo", null).belongsToAnInstitution()).isFalse();
		assertThat(new ReaderIdentity("user_solo", "  ").belongsToAnInstitution()).isFalse();
		assertThat(READER.belongsToAnInstitution()).isTrue();
	}

	private void givenCursor(ChangeCursor cursor) {
		when(changeFeed.currentCursor(READER.userId())).thenReturn(cursor);
	}

	private void givenLoans(ActiveLoanView... loans) {
		when(activeLoans.findAllFor(org.mockito.ArgumentMatchers.anyString()))
				.thenReturn(List.of(loans));
	}

}
