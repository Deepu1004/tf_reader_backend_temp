package com.tf.reader.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.CollectionEntitlementView;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.CollectionEntitlementAdminService;
import com.tf.reader.common.page.PageResponse;

/**
 * Every collection, paginated and tagged with entitlementStatus - the collection counterpart of
 * {@code GET /api/admin/v1/catalogue-items}, for the console's entitlement request screen.
 * Distinct from the existing publisher-scoped
 * {@code GET /api/admin/v1/publishers/{publisherId}/collections}, which is left unchanged.
 *
 * <pre>
 *   GET /api/admin/v1/collections
 * </pre>
 */
@RestController("collectionEntitlementAdminController")
@RequestMapping("/api/admin/v1/collections")
public class CollectionEntitlementAdminController {

	private final CollectionEntitlementAdminService collections;
	private final AdminScopeAuthorizer adminScope;

	public CollectionEntitlementAdminController(CollectionEntitlementAdminService collections,
			AdminScopeAuthorizer adminScope) {
		this.collections = collections;
		this.adminScope = adminScope;
	}

	@GetMapping
	public PageResponse<CollectionEntitlementView> list(@RequestParam(required = false) String publisherId,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String institutionId) {

		String publisherIdScope = adminScope.currentRole() == AdminRole.PUBLISHER_ADMIN
				? adminScope.currentPublisherScope()
				: null;
		return collections.list(publisherIdScope, publisherId, page, size, institutionId);
	}

}
