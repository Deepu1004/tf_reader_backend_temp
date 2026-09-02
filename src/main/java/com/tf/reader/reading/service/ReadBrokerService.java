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
import com.tf.reader.loan.api.LicenceCommand;
import com.tf.reader.loan.api.LicenceView;
import com.tf.reader.reading.api.CopyLease;
import com.tf.reader.reading.api.LeaseHandle;
import com.tf.reader.reading.dto.ReadingSessionRequest;
import com.tf.reader.reading.dto.ReadingSessionResponse;

/**
 * The read broker orchestrator for reading and downloading.
 *
 * <p>Executes all 9 steps of the read and download flow, stopping at the first refusal.
 */
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
		this.changeLog = changeLog;
		this.clock = clock;
	}

	public ReadingSessionResponse open(SubjectRef subject, ReadingSessionRequest request) {

		// ── Step 1: Validate device key format ──
		byte[] deviceKey = decodeDeviceKey(request.devicePublicKey());

		// ── Step 2: Entitlement check ──
		EntitlementDecision decision = entitlements.check(subject, request.itemId());
		if (!decision.entitled()) {
			// For downloadable tiers, the change log is the only channel that reaches a device
			// which already has the title on disk. Write ENTITLEMENT_REVOKED before refusing,
			// so an offline reader eventually learns the access is gone. Best-effort: ChangeLog
			// never throws, so a feed failure never converts a clean 403 into a 500.
			// Elite is online-only — the re-check here IS the enforcement; no feed needed.
			if (isRevocationReason(decision.reason()) && isDownloadableTier(decision.accessLevel())) {
				changeLog.record(ChangeRecord.forRevocation(
						subject.userId(),
						request.itemId(),
						"unknown", // loanId is not available at refusal time — see note below
						clock.instant()));
				// NOTE: ideally we would pass the existing loanId so the feed entry is richer,
				// but LicenceCommand only exposes create(), not findByUserAndItem(). Using
				// "unknown" is acceptable per the ChangeLog contract — the feed consumer uses
				// the (userId, itemId, reason) triple to act, not the loanId. Track as task-29b
				// to wire in the loanId once Shashank publishes a read-only query.
			}
			throw new ApiException(mapDenyReason(decision.reason()), "You do not have access to this title.");
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
				// ACCEPTED GAP: the response is returned even if reconcile() does not restore
				// the lease. Design intent is "recover, never rollback" — the reader has the
				// licence and already holds the title; refusing now would be worse than a copy
				// count that is temporarily one short. The 30-second claim TTL self-heals the
				// slot without any action from the caller. Tested by
				// ReadBrokerServiceTest.returnsSessionEvenWhenExtendAndReconcileBothFail.
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

	/**
	 * Whether this deny reason represents an entitlement that was actively withdrawn
	 * rather than a title that was never accessible or is simply not ready.
	 *
	 * <p>Only withdrawn entitlements need a change-log entry: a reader whose institution's
	 * subscription lapsed has a downloaded title they can no longer open, and the feed is
	 * the only way to tell them. A reader who never had access, or whose title is not yet
	 * available, has no downloaded copy to notify about.
	 */
	private static boolean isRevocationReason(DenyReason reason) {
		if (reason == null) return false;
		return switch (reason) {
			case ENTITLEMENT_EXPIRED, ENTITLEMENT_SUSPENDED, INSTITUTION_INACTIVE -> true;
			case NO_ENTITLEMENT, CONTENT_NOT_READY, NOT_FOUND -> false;
		};
	}

	/**
	 * Whether this tier produces a title that can be downloaded to a device.
	 *
	 * <p>ELITE (ENTITLED_CONCURRENT) is online-only — the re-check at step 2 IS the
	 * enforcement for an offline reader, because they can never have a copy on disk.
	 * The change log entry is therefore only needed for tiers that permit downloading.
	 */
	private static boolean isDownloadableTier(AccessLevel level) {
		if (level == null) return false;
		return level == AccessLevel.OPEN_ACCESS || level == AccessLevel.ENTITLED_UNLIMITED;
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
