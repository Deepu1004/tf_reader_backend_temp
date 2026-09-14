package com.tf.reader.auth.saml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.tf.reader.auth.model.TnfUser;
import com.tf.reader.auth.model.UserType;
import com.tf.reader.auth.repository.ReaderSessionRepository;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

/**
 * The mapper is where "no per-student account" is actually decided: a device either presents an
 * id it already holds, or it is minted a fresh one - never resolved against a directory, never
 * an email or username. The one thing it enforces is the institution's concurrent-seat cap, and
 * only for a device asking for a new seat.
 */
class SamlUserMapperTest {

	private static final Instant NOW = Instant.parse("2026-08-13T09:00:00Z");
	private static final int MAX_SEATS = 2;

	private final ReaderSessionRepository readerSessions = mock(ReaderSessionRepository.class);
	private final SamlUserMapper mapper =
			new SamlUserMapper(readerSessions, Clock.fixed(NOW, ZoneOffset.UTC), MAX_SEATS);

	@Test
	void aReturningDeviceKeepsItsOwnId() {
		// Presenting an id it already holds is never checked against the cap - refusing a device
		// already in use, just because the cap has since filled up, would read as a bug.
		when(readerSessions.countByInstitutionIdAndRevokedAtIsNullAndExpiresAtAfter("inst_7f3", NOW))
				.thenReturn((long) MAX_SEATS);

		TnfUser user = mapper.map("inst_7f3", "dev_abc123");

		assertThat(user.userId()).isEqualTo("dev_abc123");
		assertThat(user.type()).isEqualTo(UserType.INSTITUTION);
		assertThat(user.institutionId()).isEqualTo("inst_7f3");
		assertThat(user.roles()).containsExactly("MEMBER");
		assertThat(user.collections()).isEmpty();
	}

	@Test
	void aNewDeviceIsMintedAFreshIdUnderTheCap() {
		when(readerSessions.countByInstitutionIdAndRevokedAtIsNullAndExpiresAtAfter("inst_7f3", NOW))
				.thenReturn(0L);

		TnfUser first = mapper.map("inst_7f3", null);
		TnfUser second = mapper.map("inst_7f3", "");

		assertThat(first.userId()).startsWith("dev_");
		assertThat(second.userId()).startsWith("dev_");
		assertThat(first.userId()).isNotEqualTo(second.userId());
	}

	@Test
	void refusesANewDeviceWhenTheInstitutionHasNoSeatFree() {
		when(readerSessions.countByInstitutionIdAndRevokedAtIsNullAndExpiresAtAfter("inst_7f3", NOW))
				.thenReturn((long) MAX_SEATS);

		assertThatThrownBy(() -> mapper.map("inst_7f3", null))
				.isInstanceOf(ApiException.class)
				.extracting(thrown -> ((ApiException) thrown).getCode())
				.isEqualTo(ErrorCode.SEAT_LIMIT_REACHED);
	}

	@Test
	void producesNoIdentityOfAnyKindBesidesTheOpaqueDeviceId() {
		// TnfUser is an identity, not a credential - and never a username or email either.
		assertThat(TnfUser.class.getRecordComponents())
				.extracting(RecordComponent::getName)
				.containsExactly("userId", "type", "institutionId", "roles", "collections");
	}
}
