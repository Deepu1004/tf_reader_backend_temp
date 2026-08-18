package com.tf.reader.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class AdminAuditWriterTest {

	private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
	private final AdminAuditWriter writer = new AdminAuditWriter(auditLogRepository);

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void savesTheChangedFieldsWithTheActorIdFromTheJwtSubject() {
		authenticateAs("adm_01");

		writer.record(AuditLog.Action.STATUS, "PUBLISHER", "pub_5",
				Map.of("status", "ACTIVE"), Map.of("status", "SUSPENDED"));

		AuditLog saved = savedLog();
		assertThat(saved.getActorId()).isEqualTo("adm_01");
		assertThat(saved.getActorEmail()).isNull();
		assertThat(saved.getAction()).isEqualTo(AuditLog.Action.STATUS);
		assertThat(saved.getEntityType()).isEqualTo("PUBLISHER");
		assertThat(saved.getEntityId()).isEqualTo("pub_5");
		assertThat(saved.getBefore()).containsEntry("status", "ACTIVE");
		assertThat(saved.getAfter()).containsEntry("status", "SUSPENDED");
		assertThat(saved.getAt()).isNotNull();
	}

	@Test
	void writesWithNoActorIdWhenThereIsNoAuthenticatedAdmin() {
		SecurityContextHolder.clearContext();

		writer.record(AuditLog.Action.STATUS, "PUBLISHER", "pub_5", Map.of(), Map.of());

		assertThat(savedLog().getActorId()).isNull();
	}

	@Test
	void storesOnlyTheFieldsItWasGivenAndNeverAPasswordOrToken() {
		authenticateAs("adm_02");
		Map<String, Object> before = Map.of("status", "ACTIVE", "passwordHash", "old-hash");
		Map<String, Object> after = Map.of(
				"status", "SUSPENDED", "token", "t", "accessToken", "a", "refreshToken", "r");

		writer.record(AuditLog.Action.UPDATE, "ADMIN_USER", "adm_9", before, after);

		AuditLog saved = savedLog();
		assertThat(saved.getBefore()).containsOnlyKeys("status");
		assertThat(saved.getAfter()).containsOnlyKeys("status");
	}

	private AuditLog savedLog() {
		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		return captor.getValue();
	}

	private static void authenticateAs(String adminId) {
		Jwt jwt = mock(Jwt.class);
		when(jwt.getSubject()).thenReturn(adminId);
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(jwt, null, "ROLE_ADMIN"));
	}

}
