package com.tf.reader.reading.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.tf.reader.content.api.Encryption;
import com.tf.reader.content.api.IndexUrl;
import com.tf.reader.content.api.SignedUrl;

/**
 * Response body for {@code POST /api/v1/reading-sessions} — the licence and the content grant,
 * in one response, for the app and the reader team.
 *
 * <p><b>{@code content}, {@code index} and {@code encryption} are the catalogue team's own records,
 * forwarded field for field.</b> They are deliberately not redefined here. Redefining them is
 * exactly how a field gets renamed, and renaming {@code wrappedBek} breaks decryption on the device
 * and surfaces as "this file is corrupt" rather than as an error anywhere near us.
 *
 * <p>Nulls are omitted rather than serialised, so a consumer tests for presence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingSessionResponse(

		/** For correlating logs with a support report. Not a credential, and never presented back. */
		String sessionId,

		String loanId,
		String itemId,

		/** What the entitlement check returned: OPEN_ACCESS, ENTITLED_UNLIMITED, ENTITLED_CONCURRENT. */
		String accessLevel,

		/**
		 * The same tier in the vocabulary the app already speaks from the catalogue feeds:
		 * OPEN_ACCESS, SUBSCRIPTION, ELITE. The two enums are both committed and they are not the
		 * same, so the translation happens once, in the broker, and travels here.
		 */
		String licenceModel,

		/** <b>Use this for the download button, not the tier.</b> The server refuses either way. */
		boolean canPersist,

		/**
		 * Present only for an ELITE title: when the reader's place in the queue was created (or,
		 * if they were already queued, when it originally was). Absent for every other tier and
		 * absent for an ELITE title that got a free copy directly — there is no hold in that case.
		 */
		Instant holdCreatedAt,

		SignedUrl content,
		IndexUrl index,
		Encryption encryption,

		/** This session, about five minutes. Not the loan, and not the signed URL. Absent while queued. */
		Instant expiresAt,

		Instant serverTime) {
}
