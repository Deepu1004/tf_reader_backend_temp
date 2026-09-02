package com.tf.reader.reading.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.catalogue.api.DenyReason;
import com.tf.reader.catalogue.api.EntitlementDecision;
import com.tf.reader.catalogue.api.EntitlementQuery;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.content.api.ContentAccessGrant;
import com.tf.reader.content.api.ContentGrant;
import com.tf.reader.content.api.ContentGrantRequest;
import com.tf.reader.content.api.LoanProof;
import com.tf.reader.hold.api.AvailabilityQuery;
import com.tf.reader.hold.api.AvailabilitySnapshot;
import com.tf.reader.library.api.ChangeLog;
import com.tf.reader.library.api.ChangeRecord;
import com.tf.reader.loan.api.ActiveLoanQuery;
import com.tf.reader.loan.api.LicenceCommand;
import com.tf.reader.loan.api.LicenceView;
import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;
import com.tf.reader.reading.dto.ReadingSessionRequest;
import com.tf.reader.reading.dto.ReadingSessionResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * The read broker orchestrator for reading and downloading.
 *
 * <p>Executes all 9 steps of the read and download flow, stopping at the first refusal.
 *
 * <p><b>ENTITLEMENT_REVOKED feed entry (task 29 — resolved).</b> When Step 2 finds that the
 * reader's entitlement has been withdrawn ({@link DenyReason#ENTITLEMENT_EXPIRED} or
 * {@link DenyReason#ENTITLEMENT_SUSPENDED}), the broker writes a best-effort
 * {@link com.tf.reader.library.api.ChangeReason#ENTITLEMENT_REVOKED} entry into the change feed
 * before throwing the 4xx refusal. This is the only way an offline or downloaded title can learn
 * its access has been removed — a device that never calls back into the broker would never receive
 * the denial otherwise. The entry is written after the exception is constructed (never before),
 * and any failure to write it is logged and silently swallowed per the ChangeLog contract —
 * it never converts a clean 403 into a 500.
 *
 * <p>Reasons that do <em>not</em> emit the entry: {@link DenyReason#NO_ENTITLEMENT} (the reader
 * never had access — there is no active loan to revoke), {@link DenyReason#NOT_FOUND},
 * {@link DenyReason#INSTITUTION_INACTIVE}, {@link DenyReason#CONTENT_NOT_READY} (system/catalogue
 * states, not revocations of a held right).
 */
@Slf4j
@Service
public class ReadBrokerService {

	private static final Duration SESSION_TTL = Duration.ofMinutes(5);

	private final EntitlementQuery entitlements;
	private final ContentAccessGrant content;
	private final LicenceCommand licences;
	private final CopyLease lease;
	private final AvailabilityQuery availability;
	private final ReconcilerService reconciler;
	private final DeviceCapService devices;
	private final RightsService rights;
	private final ActiveLoanQuery activeLoans;
	private final ChangeLog changeLog;
	private final Clock clock;

	public ReadBrokerService(
			EntitlementQuery entitlements,
			ContentAccessGrant content,
			LicenceCommand licences,
			CopyLease lease,
			AvailabilityQuery availability,
			ReconcilerService reconciler,
			DeviceCapService devices,
			RightsService rights,
			ActiveLoanQuery activeLoans,
			ChangeLog changeLog,
			Clock clock) {
		this.entitlements = entitlements;
		this.content = content;
		this.licences = licences;
		this.lease = lease;
		this.availability = availability;
		this.reconciler = reconciler;
		this.devices = devices;
		this.rights = rights;
		this.activeLoans = activeLoans;
		this.changeLog = changeLog;
		this.clock = clock;
	}

	public ReadingSessionResponse open(SubjectRef subject, ReadingSessionRequest request) {

		// ── Step 1: Validate device key format ──
		byte[] deviceKey = decodeDeviceKey(request.devicePublicKey());

		// ── Step 2: Entitlement check ──
		EntitlementDecision decision = entitlements.check(subject, request.itemId());
		if (!decision.entitled()) {
			ApiException refusal = new ApiException(
					mapDenyReason(decision.reason()), "You do not have access to this title.");
			// A revocation (expired or suspended) is the one denial the device cannot infer from
			// its own actions — it may be mid-read on a downloaded copy that will never call back
			// into the broker. Write the feed entry AFTER constructing the exception so any feed
			// failure never converts this into a 500. Best-effort: ChangeLog never throws.
			recordRevocationIfActive(decision.reason(), subject, request.itemId());
			throw refusal;
		}

		// ── Step 3: Device cap check (ELITE only) ──
		// Open access and subscription access are not copy/device limited. Only an Elite
		// entitlement consumes a concurrent-reading device slot.
		if (decision.accessLevel() == AccessLevel.ENTITLED_CONCURRENT
				&& subject != null && subject.userId() != null && !devices.admit(subject.userId(), deviceKey)) {
			throw new ApiException(ErrorCode.DEVICE_LIMIT_REACHED, "This account is already reading on the maximum number of devices.");
		}

		// ── Step 4: Rights check ──
		rights.check(decision.accessLevel(), request.intent(), request.format());

		// ── Step 5: Claim copy (ELITE / ENTITLED_CONCURRENT only) ──
		boolean copyLimited = decision.accessLevel() == AccessLevel.ENTITLED_CONCURRENT;
		LeaseHandle held = null;

		if (copyLimited) {
			String scope = subject != null ? subject.institutionId() : null;
			held = lease.claim(scope, request.itemId(), decision.copies())
					.orElseThrow(() -> noCopies(scope, request.itemId(), decision.copies()));
		}

		try {
			// ── Step 6: Create licence ──
			LicenceView licence = licences.create(
					subject,
					request.itemId(),
					decision.accessLevel(),
					decision.loanPeriodDays(),
					held == null ? null : held.token()
			);

			// ── Step 7: Fetch content grant ──
			ContentGrant grant = content.grant(new ContentGrantRequest(
					request.itemId(),
					request.format(),
					request.intent(),
					deviceKey,
					subject,
					new LoanProof(licence.licenceId(), licence.expiresAt()),
					request.wantSearchIndex()
			));

			// ── Step 8: Extend claim to THIS READING SESSION's own lifetime, not the loan's due
			// date. A copy lease means "actively reading right now" — the loan's dueAt is null
			// for ELITE (no subscription-style due date at all, confirmed by NPE here against a
			// real re-fetched ELITE loan, 2026-08-25) and, for SUBSCRIPTION, days-to-weeks away,
			// which would hold a copy hostage for the whole loan period on a single open instead
			// of releasing it when the session ends — defeating the copy limit's own purpose.
			Instant now = clock.instant();
			Instant sessionExpiresAt = now.plus(SESSION_TTL);
			if (copyLimited && !lease.extend(held, sessionExpiresAt)) {
				reconciler.reconcile(request.itemId());
			}

			// ── Step 9: Forward payload unchanged ──
			return new ReadingSessionResponse(
					"sess_" + UUID.randomUUID().toString().substring(0, 8),
					licence.licenceId(),
					request.itemId(),
					decision.accessLevel().name(),
					licenceModelOf(decision.accessLevel()),
					licence.canPersist(),
					null,
					grant.content(),
					grant.index(),
					grant.encryption(),
					sessionExpiresAt,
					now
			);

		} catch (RuntimeException failure) {
			if (held != null) {
				lease.release(held);
			}
			throw failure;
		}
	}

	private ApiException noCopies(String scope, String itemId, Integer copies) {
		String detail = "";
		try {
			AvailabilitySnapshot snapshot = availability.forItem(scope, itemId, copies != null ? copies : 1);
			if (snapshot != null) {
				Integer q = snapshot.queueLength();
				if (q != null) {
					detail = " " + q + " reader(s) are waiting.";
					if (snapshot.myPosition() != null) {
						detail += " You are already " + snapshot.myPosition() + " in line.";
					} else {
						detail += " You can join the queue at POST /api/v1/holds.";
					}
				}
			}
		} catch (RuntimeException ignored) {
			// best effort only — never convert 409 into 500
		}
		return new ApiException(ErrorCode.NO_COPIES_AVAILABLE, "All copies of this title are on loan." + detail);
	}

	private byte[] decodeDeviceKey(String base64) {
		try {
			byte[] raw = Base64.getDecoder().decode(base64);
			if (raw.length == 0) {
				throw new IllegalArgumentException("empty");
			}
			return raw;
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.INVALID_DEVICE_PUBLIC_KEY, "devicePublicKey must be valid base64 of the raw public key bytes.");
		}
	}

	/**
	 * Writes an {@code ENTITLEMENT_REVOKED} entry into the change feed when the denial reason is
	 * a true revocation — the reader's right was withdrawn while they still held a loan.
	 *
	 * <p>Only {@link DenyReason#ENTITLEMENT_EXPIRED} and {@link DenyReason#ENTITLEMENT_SUSPENDED}
	 * qualify. The other reasons ({@code NO_ENTITLEMENT}, {@code NOT_FOUND},
	 * {@code INSTITUTION_INACTIVE}, {@code CONTENT_NOT_READY}) are not revocations — there was
	 * either never a held loan to revoke, or the state is a catalogue/system issue unrelated to
	 * the reader's individual entitlement.
	 *
	 * <p>The active-loan lookup is best-effort: if the reader has no active loan for this item
	 * (possible if the loan was already swept), the entry is skipped rather than written with a
	 * null loanId that {@link ChangeRecord} would reject.
	 */
	private void recordRevocationIfActive(DenyReason reason, SubjectRef subject, String itemId) {
		if (reason != DenyReason.ENTITLEMENT_EXPIRED && reason != DenyReason.ENTITLEMENT_SUSPENDED) {
			return;
		}
		if (subject == null || subject.userId() == null) {
			return;
		}
		try {
			activeLoans.findActive(subject.userId(), itemId).ifPresent(loan ->
					changeLog.record(ChangeRecord.forRevocation(
							subject.userId(), itemId, loan.loanId(), clock.instant())));
		} catch (RuntimeException ex) {
			// Best-effort: a feed write failure must never convert a clean 4xx into a 5xx.
			// ChangeLog.record() itself already swallows, but the ActiveLoanQuery call can
			// still throw (Mongo unreachable, etc.) — catch here so the refusal stays clean.
			log.error("revocation feed write failed, reader={} item={} — entry lost",
					subject.userId(), itemId, ex);
		}
	}

	private static ErrorCode mapDenyReason(DenyReason reason) {
		if (reason == null) return ErrorCode.NO_ENTITLEMENT;
		return switch (reason) {
			case NO_ENTITLEMENT -> ErrorCode.NO_ENTITLEMENT;
			case ENTITLEMENT_EXPIRED -> ErrorCode.ENTITLEMENT_EXPIRED;
			case ENTITLEMENT_SUSPENDED -> ErrorCode.ENTITLEMENT_SUSPENDED;
			case INSTITUTION_INACTIVE -> ErrorCode.INSTITUTION_INACTIVE;
			case CONTENT_NOT_READY -> ErrorCode.CONTENT_NOT_READY;
			case NOT_FOUND -> ErrorCode.NOT_FOUND;
		};
	}

	private static String licenceModelOf(AccessLevel level) {
		if (level == null) return "SUBSCRIPTION";
		return switch (level) {
			case OPEN_ACCESS -> "OPEN_ACCESS";
			case ENTITLED_UNLIMITED -> "SUBSCRIPTION";
			case ENTITLED_CONCURRENT -> "ELITE";
		};
	}
}
