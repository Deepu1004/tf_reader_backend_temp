package com.tf.reader.reading.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.tf.reader.content.api.Encryption;
import com.tf.reader.content.api.IndexUrl;
import com.tf.reader.content.api.SignedUrl;

/**
 * Response body for {@code POST /api/v1/reading-sessions} — the license, the queue and the content
 * grant, in one response, for the app and the reader team.
 *
 * <p><b>{@code content}, {@code index} and {@code encryption} are the catalogue team's own records,
 * forwarded field for field.</b> They are deliberately not redefined here. Redefining them is
 * exactly how a field gets renamed, and renaming {@code wrappedBek} breaks decryption on the device
 * and surfaces as "this file is corrupt" rather than as an error anywhere near us.
 *
 * <p>Nulls are omitted rather than serialised, so a consumer tests for presence. A
 * {@code "queue": null} would read as "there is a queue whose state we lost" instead of "this tier
 * has no queue".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingSessionResponse(

		/** For correlating logs with a support report. Not a credential, and never presented back. */
		String sessionId,

		String licenseId,
		String itemId,

		/** What the entitlement check returned: OPEN_ACCESS, ENTITLED_UNLIMITED, ENTITLED_CONCURRENT. */
		String accessLevel,

		/**
		 * The same tier in the vocabulary the app already speaks from the catalogue feeds:
		 * OPEN_ACCESS, SUBSCRIPTION, ELITE. The two enums are both committed and they are not the
		 * same, so the translation happens once, in the broker, and travels here.
		 */
		String licenseModel,

		/** <b>Use this for the download button, not the tier.</b> The server refuses either way. */
		boolean canPersist,

		/** Absent entirely for open access and subscription — those tiers have no queue. */
		QueueState queue,

		SignedUrl content,
		IndexUrl index,
		Encryption encryption,

		/** This session, about five minutes. Not the loan, and not the signed URL. */
		Instant expiresAt,

		Instant serverTime) {

	/**
	 * Where the reader stands, for a copy-limited title.
	 *
	 * <p>{@code position} is 0 when a copy is free and the reader may go now. Anything higher means
	 * wait — and the {@code content} URL in this response will have expired by the time they are
	 * promoted, so the app must call again rather than cache it.
	 *
	 * <p>{@code estimatedAt} is a guess and is named like one. It knows nothing about early returns
	 * and must never be rendered as a promise.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record QueueState(
			String queueId,
			int position,
			int queueLength,
			boolean readNow,
			Instant estimatedAt) {
	}
}
