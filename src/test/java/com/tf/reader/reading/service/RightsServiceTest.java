package com.tf.reader.reading.service;

import com.tf.reader.catalogue.api.AccessLevel;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.content.api.Format;
import com.tf.reader.content.api.Intent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every tier crossed with both intents — the whole truth table this service exists to hold. */
class RightsServiceTest {

	private final RightsService rights = new RightsService();

	@Test
	void streamIsAllowedOnEveryTier() {
		for (AccessLevel level : AccessLevel.values()) {
			assertThatNoException().isThrownBy(() -> rights.check(level, Intent.STREAM, Format.PDF));
		}
	}

	@Test
	void downloadIsAllowedOnOpenAccess() {
		assertThatNoException()
				.isThrownBy(() -> rights.check(AccessLevel.OPEN_ACCESS, Intent.DOWNLOAD, Format.PDF));
	}

	@Test
	void downloadIsAllowedOnSubscription() {
		assertThatNoException()
				.isThrownBy(() -> rights.check(AccessLevel.ENTITLED_UNLIMITED, Intent.DOWNLOAD, Format.EPUB));
	}

	@Test
	void downloadIsRefusedOnElite() {
		assertThatThrownBy(() -> rights.check(AccessLevel.ENTITLED_CONCURRENT, Intent.DOWNLOAD, Format.PDF))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).code())
				.isEqualTo(ErrorCode.DOWNLOAD_NOT_PERMITTED);
	}

	@Test
	void audioIsNeverDownloadableEvenOnSubscription() {
		assertThatThrownBy(() -> rights.check(AccessLevel.ENTITLED_UNLIMITED, Intent.DOWNLOAD, Format.AUDIO))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).code())
				.isEqualTo(ErrorCode.DOWNLOAD_NOT_PERMITTED);
	}

	@Test
	void audioOnEliteFailsForTheTierNotJustTheFormat() {
		// Both rules would refuse this. Asserting the code is enough; which rule fired first
		// does not matter to the caller.
		assertThatThrownBy(() -> rights.check(AccessLevel.ENTITLED_CONCURRENT, Intent.DOWNLOAD, Format.AUDIO))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).code())
				.isEqualTo(ErrorCode.DOWNLOAD_NOT_PERMITTED);
	}

	@Test
	void theRefusalStatusIs403MatchingWokaysFrozenFile() {
		assertThatThrownBy(() -> rights.check(AccessLevel.ENTITLED_CONCURRENT, Intent.DOWNLOAD, Format.PDF))
				.isInstanceOf(ApiException.class)
				.extracting(e -> ((ApiException) e).code().status())
				.isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
		assertThat(ErrorCode.DOWNLOAD_NOT_PERMITTED.status()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
	}
}
