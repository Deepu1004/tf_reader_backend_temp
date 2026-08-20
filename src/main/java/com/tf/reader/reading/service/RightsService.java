package com.tf.reader.reading.service;

import org.springframework.stereotype.Service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.shared.error.ApiException;
import com.tf.reader.shared.error.ErrorCode;
import com.tf.reader.content.api.Format;
import com.tf.reader.content.api.Intent;

/**
 * Enforces reading vs. downloading rights per tier and content format.
 *
 * <p>Rule 1: Download of a concurrent (ELITE) title is refused server-side with
 * {@code ErrorCode.DOWNLOAD_NOT_PERMITTED} (HTTP 403 Forbidden).
 * <p>Rule 2: Audio content is stream-only across all tiers and cannot be downloaded.
 */
@Service
public class RightsService {

	/**
	 * Validates whether the requested intent is allowed for the given tier and format.
	 *
	 * @throws ApiException with {@link ErrorCode#DOWNLOAD_NOT_PERMITTED} if disallowed.
	 */
	public void check(AccessLevel accessLevel, Intent intent, Format format) {
		if (intent == Intent.DOWNLOAD) {
			if (accessLevel == AccessLevel.ENTITLED_CONCURRENT) {
				throw new ApiException(
						ErrorCode.DOWNLOAD_NOT_PERMITTED,
						"Downloading is not permitted for concurrent (ELITE) titles. Elite titles are online-only."
				);
			}

			if (format == Format.AUDIO) {
				throw new ApiException(
						ErrorCode.DOWNLOAD_NOT_PERMITTED,
						"Audio titles cannot be downloaded."
				);
			}
		}
	}
}
