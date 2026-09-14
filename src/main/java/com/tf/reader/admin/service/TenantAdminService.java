package com.tf.reader.admin.service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tf.reader.admin.dto.DatabaseWrite;
import com.tf.reader.admin.dto.TenantView;
import com.tf.reader.admin.dto.VaultKeyWrite;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.entity.Publisher;
import com.tf.reader.catalogue.entity.VaultConnectionHealth;
import com.tf.reader.catalogue.repository.PublisherRepository;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.mongo.PublisherMongoClients;
import com.tf.reader.crypto.api.VaultKeyAdmin;

import lombok.RequiredArgsConstructor;

/**
 * T&F's own infrastructure view of a publisher: which key vault it uses and whether that vault is
 * reachable, as opposed to {@link PublisherAdminService}'s business-data view.
 *
 * <p>{@link #list()}/{@link #get} are SUPER_ADMIN only - a publisher's own admin has no need to
 * enumerate every tenant. {@link #setDatabase}/{@link #setVaultKey} are self-service instead,
 * guarded by {@link AdminScopeAuthorizer#canAccessPublisher}: this is the switch a publisher can
 * flip themselves, without T&F's help, which is the whole point of this plan.
 */
@Service
@RequiredArgsConstructor
public class TenantAdminService {

	private static final int VAULT_KEY_BYTES = 32;

	private final PublisherRepository publisherRepository;
	private final AdminScopeAuthorizer adminScope;
	private final AdminAuditWriter auditWriter;
	private final PublisherMongoClients publisherMongoClients;
	private final VaultKeyAdmin vaultKeyAdmin;

	public List<TenantView> list() {
		adminScope.requireSuperAdmin();
		return publisherRepository.findAll().stream().map(TenantAdminService::toView).toList();
	}

	public TenantView get(String publisherId) {
		adminScope.requireSuperAdmin();
		return publisherRepository.findById(publisherId).map(TenantAdminService::toView)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));
	}

	/**
	 * Opt-in, per publisher: a null/blank {@code mongoUri} reverts to T&F's shared database. Does
	 * not migrate any existing data - a publisher switching connections starts writing/reading from
	 * the new one from this point on; whatever was already in the shared database under their
	 * {@code publisherId} stays there, invisible to the new connection.
	 */
	public TenantView setDatabase(String publisherId, DatabaseWrite write) {
		requireAccess(publisherId);
		Publisher publisher = publisherRepository.findById(publisherId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));

		String newUri = blankToNull(write.mongoUri());
		publisherMongoClients.invalidate(publisherId);

		VaultConnectionHealth health;
		if (newUri == null) {
			health = VaultConnectionHealth.NOT_CONFIGURED;
		} else {
			health = publisherMongoClients.ping(newUri) ? VaultConnectionHealth.HEALTHY
					: VaultConnectionHealth.UNREACHABLE;
		}

		publisher.setMongoUri(newUri);
		publisher.setConnectionHealth(health);
		publisher.setUpdatedAt(Instant.now());
		publisher = publisherRepository.save(publisher);

		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.UPDATE, "PUBLISHER", publisherId, null,
				Map.of("connectionHealth", String.valueOf(health)));

		return toView(publisher);
	}

	/** Opt-in, per publisher: a null/blank key reverts to T&F's shared master key. */
	public TenantView setVaultKey(String publisherId, VaultKeyWrite write) {
		requireAccess(publisherId);
		Publisher publisher = publisherRepository.findById(publisherId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));

		String keyBase64 = blankToNull(write.keyBase64());
		if (keyBase64 == null) {
			vaultKeyAdmin.clearKey(publisherId);
			publisher.setVaultRef(null);
		} else {
			byte[] raw = decodeKey(keyBase64);
			vaultKeyAdmin.setKey(publisherId, raw);
			publisher.setVaultRef(publisherId);
		}
		publisher.setUpdatedAt(Instant.now());
		publisher = publisherRepository.save(publisher);

		// Never the key material itself - only that a change happened.
		auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.UPDATE, "PUBLISHER", publisherId, null,
				Map.of("vaultConfigured", keyBase64 != null));

		return toView(publisher);
	}

	private void requireAccess(String publisherId) {
		if (!adminScope.canAccessPublisher(publisherId)) {
			throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to access this publisher");
		}
	}

	private static byte[] decodeKey(String keyBase64) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(keyBase64);
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "keyBase64 is not valid base64.");
		}
		if (raw.length != VAULT_KEY_BYTES) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"keyBase64 must decode to " + VAULT_KEY_BYTES + " bytes but was " + raw.length + " bytes.");
		}
		return raw;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private static TenantView toView(Publisher publisher) {
		return new TenantView(publisher.getId(), publisher.getCode(), publisher.getName(),
				publisher.getVaultRef(), publisher.getConnectionHealth());
	}

}
