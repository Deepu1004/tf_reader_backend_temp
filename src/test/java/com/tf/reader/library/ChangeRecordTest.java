package com.tf.reader.library;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tf.reader.library.api.ChangeReason;
import com.tf.reader.library.api.ChangeRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The published record three other lanes construct, so its refusals are part of the contract.
 */
class ChangeRecordTest {

	private static final String READER = "user_9c2";
	private static final Instant AT = Instant.parse("2026-08-18T10:00:00Z");

	@Test
	@DisplayName("a loan event carries a loanId and no holdId")
	void loanEventCarriesOnlyALoanId() {
		ChangeRecord change =
				ChangeRecord.forLoan(READER, ChangeReason.LOAN_CREATED, "item_42", "loan_7c1", AT);

		assertThat(change.loanId()).isEqualTo("loan_7c1");
		assertThat(change.holdId()).isNull();
	}

	@Test
	@DisplayName("a hold event carries a holdId and no loanId")
	void holdEventCarriesOnlyAHoldId() {
		ChangeRecord change =
				ChangeRecord.forHold(READER, ChangeReason.HOLD_PROMOTED, "item_77", "hold_5d1", AT);

		assertThat(change.holdId()).isEqualTo("hold_5d1");
		assertThat(change.loanId()).isNull();
	}

	@Test
	@DisplayName("a revocation is a loan event, because it is a loan that was taken away")
	void revocationIsALoanEvent() {
		ChangeRecord change = ChangeRecord.forRevocation(READER, "item_42", "loan_7c1", AT);

		assertThat(change.reason()).isEqualTo(ChangeReason.ENTITLEMENT_REVOKED);
		assertThat(change.loanId()).isEqualTo("loan_7c1");
		assertThat(change.holdId()).isNull();
	}

	@Test
	@DisplayName("the factories refuse the identifier they are named for")
	void factoriesRequireTheirOwnIdentifier() {
		assertThatThrownBy(
				() -> ChangeRecord.forLoan(READER, ChangeReason.LOAN_CREATED, "item_42", null, AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("loanId");

		assertThatThrownBy(
				() -> ChangeRecord.forHold(READER, ChangeReason.HOLD_PLACED, "item_42", "  ", AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("holdId");
	}

	@Test
	@DisplayName("a record with no reader or no title is refused at construction")
	void refusesWhatCannotBeFiled() {
		// The caller is the only one who still knows what the entry was meant to say, so this fails
		// there rather than at write time.
		assertThatThrownBy(() -> new ChangeRecord(null, ChangeReason.LOAN_CREATED, "item_42",
				"loan_7c1", null, AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("userId");

		assertThatThrownBy(() -> new ChangeRecord(READER, ChangeReason.LOAN_CREATED, "   ",
				"loan_7c1", null, AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("itemId");
	}

	@Test
	@DisplayName("a record with no reason or no timestamp is refused too")
	void refusesAnIncompleteEvent() {
		assertThatThrownBy(
				() -> new ChangeRecord(READER, null, "item_42", "loan_7c1", null, AT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("reason");

		assertThatThrownBy(() -> new ChangeRecord(READER, ChangeReason.LOAN_CREATED, "item_42",
				"loan_7c1", null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("occurredAt");
	}

	@Test
	@DisplayName("the eight reasons are the wire enum, name for name")
	void theEightReasons() {
		// The app switches on these strings. A rename is a breaking change to team1's client, so
		// this pins the set rather than trusting review to notice.
		assertThat(ChangeReason.values()).extracting(Enum::name).containsExactlyInAnyOrder(
				"LOAN_CREATED", "LOAN_RETURNED", "LOAN_EXPIRED",
				"HOLD_PLACED", "HOLD_CANCELLED", "HOLD_PROMOTED", "HOLD_OFFER_EXPIRED",
				"ENTITLEMENT_REVOKED");
	}

}
