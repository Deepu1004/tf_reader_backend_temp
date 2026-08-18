package com.tf.reader.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * The PRD requires the application to refuse to start without a signing secret. These are the
 * tests that make that a fact rather than an intention.
 */
class JwtPropertiesTest {

	private static final String VALID = "a-test-only-signing-secret-of-sufficient-length-0123456789";

	@Test
	void refusesToStartWithNoSecret() {
		// A default secret would mean every deployment that forgot to set one shared a publicly
		// known signing key - and anything that can verify an HS256 token can mint one.
		assertThatThrownBy(() -> new JwtProperties(null, Duration.ofHours(1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TNF_JWT_SECRET");
	}

	@Test
	void refusesToStartWithABlankSecret() {
		assertThatThrownBy(() -> new JwtProperties("   ", Duration.ofHours(1)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void reportsAnUnresolvedPlaceholderAsAMissingSecret() {
		// An unset ${TNF_JWT_SECRET} binds as its own literal text, not as null. Reported as
		// "too short" it sends whoever is deploying off to lengthen a secret that never existed.
		assertThatThrownBy(() -> new JwtProperties("${TNF_JWT_SECRET}", Duration.ofHours(1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("is not set");
	}

	@Test
	void refusesASecretTooShortForHs256() {
		// HS256 is a 256-bit MAC. A short secret is a weak one, and it fails at signing time
		// rather than at startup unless it is caught here.
		assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32 bytes");
	}

	@Test
	void refusesANonPositiveLifetime() {
		assertThatThrownBy(() -> new JwtProperties(VALID, Duration.ZERO))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new JwtProperties(VALID, Duration.ofMinutes(-5)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void defaultsToTheOneHourLifetimeThePrdSpecifies() {
		assertThat(new JwtProperties(VALID, null).ttl()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void neverPrintsTheSecret() {
		// A record's generated toString() prints every component, so the default put the HS256
		// signing key in clear into anything that logged this object. Anything that can verify one
		// of our tokens can mint one, so this is every account rather than a partial disclosure.
		String printed = new JwtProperties(VALID, Duration.ofHours(1)).toString();

		assertThat(printed).doesNotContain(VALID).contains("redacted");
		assertThat(String.valueOf(new JwtProperties(VALID, null))).doesNotContain(VALID);
	}

	@Test
	void theSigningKeyDoesNotPrintItsMaterialEither() {
		assertThat(new JwtProperties(VALID, Duration.ofHours(1)).signingKey().toString())
				.doesNotContain(VALID);
	}

	@Test
	void buildsAnHmacKeyFromTheSecret() {
		assertThat(new JwtProperties(VALID, Duration.ofHours(1)).signingKey().getAlgorithm())
				.isEqualTo("HmacSHA256");
	}
}
