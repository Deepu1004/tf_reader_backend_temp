package com.tf.reader.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.DatabaseWrite;
import com.tf.reader.admin.dto.TenantView;
import com.tf.reader.admin.dto.VaultKeyWrite;
import com.tf.reader.admin.service.TenantAdminService;

/**
 * Four tenant endpoints.
 *
 * <pre>
 *   GET  /api/admin/v1/tenants                          list, SUPER_ADMIN only
 *   GET  /api/admin/v1/tenants/{publisherId}             get, SUPER_ADMIN only
 *   PUT  /api/admin/v1/tenants/{publisherId}/database    set/clear this publisher's own Mongo
 *   PUT  /api/admin/v1/tenants/{publisherId}/vault-key   set/clear this publisher's own vault key
 * </pre>
 *
 * <p>The last two are self-service: that publisher's own PUBLISHER_ADMIN may call them for their
 * own {@code publisherId}, not only a SUPER_ADMIN - the switch a publisher can flip themselves,
 * without T&F's help.
 *
 * <p>HTTP only. Every access check lives in {@link TenantAdminService}, same reasoning as every
 * other admin controller in this package: a controller-only check is bypassed the moment a second
 * entry point calls the same service.
 */
@RestController("tenantAdminController")
@RequestMapping("/api/admin/v1/tenants")
public class TenantAdminController {

	private final TenantAdminService tenants;

	public TenantAdminController(TenantAdminService tenants) {
		this.tenants = tenants;
	}

	@GetMapping
	public List<TenantView> list() {
		return tenants.list();
	}

	@GetMapping("/{publisherId}")
	public TenantView get(@PathVariable String publisherId) {
		return tenants.get(publisherId);
	}

	@PutMapping("/{publisherId}/database")
	public TenantView setDatabase(@PathVariable String publisherId, @RequestBody DatabaseWrite body) {
		return tenants.setDatabase(publisherId, body);
	}

	@PutMapping("/{publisherId}/vault-key")
	public TenantView setVaultKey(@PathVariable String publisherId, @RequestBody VaultKeyWrite body) {
		return tenants.setVaultKey(publisherId, body);
	}

}
