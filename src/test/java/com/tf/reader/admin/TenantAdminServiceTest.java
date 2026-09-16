package com.tf.reader.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.tf.reader.admin.dto.DatabaseWrite;
import com.tf.reader.admin.dto.TenantView;
import com.tf.reader.admin.dto.VaultKeyWrite;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.TenantAdminService;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.VaultConnectionHealth;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.mongo.PublisherMongoClients;
import com.tf.reader.common.security.TokenClaims;
import com.tf.reader.crypto.api.VaultKeyAdmin;

class TenantAdminServiceTest {

	private PublisherRepository publisherRepository;
	private AdminAuditWriter auditWriter;
	private PublisherMongoClients publisherMongoClients;
	private VaultKeyAdmin vaultKeyAdmin;
	private TenantAdminService service;

	@BeforeEach
	void setUp() {
		publisherRepository = mock(PublisherRepository.class);
		auditWriter = mock(AdminAuditWriter.class);
		publisherMongoClients = mock(PublisherMongoClients.class);
		vaultKeyAdmin = mock(VaultKeyAdmin.class);
		when(publisherRepository.save(any())).thenAnswer(i -> i.getArgument(0));
		service = new TenantAdminService(publisherRepository, new AdminScopeAuthorizer(), auditWriter,
				publisherMongoClients, vaultKeyAdmin);
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsAllTenantsWhenCallerIsSuperAdmin() {
		actingAs(AdminRole.SUPER_ADMIN, null);
		when(publisherRepository.findAll()).thenReturn(List.of(routledge()));

		List<TenantView> tenants = service.list();

		assertThat(tenants).extracting(TenantView::id, TenantView::connectionHealth)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("pub_r1", VaultConnectionHealth.NOT_CONFIGURED));
	}

	@Test
	void deniesAccessWhenCallerIsNotSuperAdmin() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");

		assertThatThrownBy(() -> service.list())
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getCode())
				.isEqualTo(ErrorCode.FORBIDDEN_ROLE);
	}

	@Test
	void returnsNotFoundWhenTenantDoesNotExist() {
		actingAs(AdminRole.SUPER_ADMIN, null);
		when(publisherRepository.findById("pub_missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get("pub_missing"))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	void aPublisherAdminCanReadTheirOwnTenantStatus() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));

		TenantView view = service.get("pub_r1");

		assertThat(view.id()).isEqualTo("pub_r1");
	}

	@Test
	void aPublisherAdminCannotReadAnotherPublishersTenantStatus() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");

		assertThatThrownBy(() -> service.get("pub_other"))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getCode())
				.isEqualTo(ErrorCode.FORBIDDEN_ROLE);
	}

	@Test
	void aPublisherAdminCanSetTheirOwnMongoConnection() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		when(publisherMongoClients.ping("mongodb://localhost:27018/pub01")).thenReturn(true);

		TenantView view = service.setDatabase("pub_r1", new DatabaseWrite("mongodb://localhost:27018/pub01"));

		assertThat(view.connectionHealth()).isEqualTo(VaultConnectionHealth.HEALTHY);
		verify(publisherMongoClients).invalidate("pub_r1");
	}

	@Test
	void aPublisherAdminCannotSetAnotherPublishersMongoConnection() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");

		assertThatThrownBy(() -> service.setDatabase("pub_other", new DatabaseWrite("mongodb://localhost:27018/x")))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getCode())
				.isEqualTo(ErrorCode.FORBIDDEN_ROLE);
	}

	@Test
	void unreachableMongoIsRecordedAsUnreachable() {
		actingAs(AdminRole.SUPER_ADMIN, null);
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		when(publisherMongoClients.ping(any())).thenReturn(false);

		TenantView view = service.setDatabase("pub_r1", new DatabaseWrite("mongodb://nowhere/db"));

		assertThat(view.connectionHealth()).isEqualTo(VaultConnectionHealth.UNREACHABLE);
	}

	@Test
	void clearingTheMongoUriRevertsToNotConfigured() {
		actingAs(AdminRole.SUPER_ADMIN, null);
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));

		TenantView view = service.setDatabase("pub_r1", new DatabaseWrite(null));

		assertThat(view.connectionHealth()).isEqualTo(VaultConnectionHealth.NOT_CONFIGURED);
	}

	@Test
	void aPublisherAdminCanSetTheirOwnVaultKey() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		String key32Bytes = java.util.Base64.getEncoder().encodeToString(new byte[32]);

		TenantView view = service.setVaultKey("pub_r1", new VaultKeyWrite(key32Bytes));

		assertThat(view.vaultRef()).isEqualTo("pub_r1");
		verify(vaultKeyAdmin).setKey(eq("pub_r1"), any());
	}

	@Test
	void rejectsAVaultKeyThatIsNotThirtyTwoBytes() {
		actingAs(AdminRole.PUBLISHER_ADMIN, "pub_r1");
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(routledge()));
		String tooShort = java.util.Base64.getEncoder().encodeToString(new byte[16]);

		assertThatThrownBy(() -> service.setVaultKey("pub_r1", new VaultKeyWrite(tooShort)))
				.isInstanceOf(ApiException.class)
				.extracting(ex -> ((ApiException) ex).getCode())
				.isEqualTo(ErrorCode.VALIDATION_FAILED);
		verify(vaultKeyAdmin, never()).setKey(any(), any());
	}

	@Test
	void clearingTheVaultKeyRevertsToTheSharedMasterKey() {
		actingAs(AdminRole.SUPER_ADMIN, null);
		Publisher withKey = routledge();
		withKey.setVaultRef("pub_r1");
		when(publisherRepository.findById("pub_r1")).thenReturn(Optional.of(withKey));

		TenantView view = service.setVaultKey("pub_r1", new VaultKeyWrite(null));

		assertThat(view.vaultRef()).isNull();
		verify(vaultKeyAdmin).clearKey("pub_r1");
	}

	private static void actingAs(AdminRole role, String publisherId) {
		Jwt.Builder tokenBuilder = Jwt.withTokenValue("token")
				.header("alg", "none")
				.subject("adm_test")
				.claim(TokenClaims.ROLE, role.name())
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plusSeconds(3600));
		if (publisherId != null) {
			tokenBuilder.claim(TokenClaims.SCOPE_PUBLISHER_ID, publisherId);
		}
		SecurityContextHolder.getContext()
				.setAuthentication(new TestingAuthenticationToken(tokenBuilder.build(), null, "ROLE_ADMIN"));
	}

	private static Publisher routledge() {
		Publisher p = new Publisher();
		p.setCode("routledge");
		p.setName("Routledge");
		p.setStatus(RecordStatus.ACTIVE);
		try {
			var idField = Publisher.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(p, "pub_r1");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return p;
	}

}
