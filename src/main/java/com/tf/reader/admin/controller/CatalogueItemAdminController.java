package com.tf.reader.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.CatalogueItemView;
import com.tf.reader.admin.dto.CatalogueItemWrite;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminRoles;
import com.tf.reader.admin.service.CatalogueItemAdminService;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.common.security.TokenClaims;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/admin/v1/catalogue-items")
public class CatalogueItemAdminController {

	private final CatalogueItemAdminService catalogueItems;

	public CatalogueItemAdminController(CatalogueItemAdminService catalogueItems) {
		this.catalogueItems = catalogueItems;
	}

	@GetMapping
	@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PUBLISHER_ADMIN')")
	public PageResponse<CatalogueItemView> list(@RequestParam(required = false) String publisherId,
			@RequestParam(required = false) String collectionId,
			@RequestParam(required = false) ContentType contentType,
			@RequestParam(required = false) AccessTier accessTier, @RequestParam(required = false) String q,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return catalogueItems.list(currentPublisherScope(), publisherId, collectionId, contentType, accessTier, q,
				page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CatalogueItemView create(@Valid @RequestBody CatalogueItemWrite body) {
		return catalogueItems.create(body);
	}

	@GetMapping("/{itemId}")
	public CatalogueItemView get(@PathVariable String itemId) {
		return catalogueItems.get(itemId);
	}

	@PutMapping("/{itemId}")
	public CatalogueItemView update(@PathVariable String itemId, @Valid @RequestBody CatalogueItemWrite body) {
		return catalogueItems.update(itemId, body);
	}


	private static String currentPublisherScope() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof Jwt jwt)) {
			return "no-publisher-claim";
		}

		AdminRole role = AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
		if (role == AdminRole.SUPER_ADMIN) {
			return null;
		}
		if (role != AdminRole.PUBLISHER_ADMIN) {
			return "no-publisher-claim";
		}

		String scope = jwt.getClaimAsString(TokenClaims.SCOPE_PUBLISHER_ID);
		return (scope == null || scope.isBlank()) ? "no-publisher-claim" : scope;
	}

}
