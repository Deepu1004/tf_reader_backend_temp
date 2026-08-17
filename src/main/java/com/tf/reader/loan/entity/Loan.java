package com.tf.reader.loan.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single loan — our record that a reader holds a title, under some model, until some time.
 * One uniform shape for every {@link LicenceModel}; unused fields stay {@code null} (D-017/D-018).
 *
 * <p>Two invariants are enforced by the store, never by read-then-write application code:
 * <ul>
 *   <li><b>uniq_active_user_item</b> — a partial unique index means at most one ACTIVE loan can
 *       exist per {@code (userId, itemId)}. A second insert races into an {@code E11000}
 *       DuplicateKeyException, which the create flow turns into a re-read (D-003).</li>
 *   <li><b>status_dueAt</b> — feeds the expiry sweeper's {@code status=ACTIVE && dueAt<=now}
 *       scan (D-005).</li>
 * </ul>
 *
 * <p>Every timestamp is written from the injected {@code Clock}, never a client value (invariant #4).
 */
@Document(collection = "loans")
@CompoundIndexes({
		@CompoundIndex(name = "uniq_active_user_item", def = "{'userId': 1, 'itemId': 1}",
				unique = true, partialFilter = "{ 'status': 'ACTIVE' }"),
		@CompoundIndex(name = "status_dueAt", def = "{'status': 1, 'dueAt': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan {

	/** Our id, minted on create (e.g. {@code loan_...}). */
	@Id
	private String loanId;

	/** Title id, as the app sent it (ISBN/ISSN/AISBN). */
	private String itemId;

	/** Owner of the loan. Always from the JWT, never the request body (invariant #5). */
	private String userId;

	/** Institution the access was granted under; {@code null} for personal, non-institutional access. */
	private String institutionId;

	/** How access is bounded. From entitlement, not the client (D-009). */
	private LicenceModel licenceModel;

	/** Lifecycle state; drives the partial unique index. */
	private LoanStatus status;

	/** Whether the reader may download/keep. {@code true} for Subscription/Open-Access, {@code false} for Elite. */
	private boolean canPersist;

	/**
	 * Handle to the Redis lease this loan holds. Only ever populated for {@link LicenceModel#ELITE},
	 * and even then it starts {@code null} on our create — Deepak's lease service fills it in (D-018).
	 */
	private String leaseId;

	/** When the loan was created, on the server clock. */
	private Instant borrowedAt;

	/** Scheduled end. {@code null} = open-ended (Subscription/Open-Access); always set for Elite. */
	private Instant dueAt;

	/** Actual end when the reader returned or access was revoked; else {@code null}. Fact, not plan (D-005). */
	private Instant returnedAt;

	/** Actual end stamped by the sweeper when the clock ran out; else {@code null}. Fact, not plan (D-005). */
	private Instant expiredAt;
}
