package com.tf.reader.admin.controller;

import org.springframework.http.HttpStatus;
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
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.CatalogueItemAdminService;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.common.page.PageResponse;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/admin/v1/catalogue-items")
public class CatalogueItemAdminController {

	private final CatalogueItemAdminService catalogueItems;
	private final AdminScopeAuthorizer adminScope;

	public CatalogueItemAdminController(CatalogueItemAdminService catalogueItems, AdminScopeAuthorizer adminScope) {
		this.catalogueItems = catalogueItems;
		this.adminScope = adminScope;
	}

	@GetMapping
	public PageResponse<CatalogueItemView> list(@RequestParam(required = false) String publisherId,
			@RequestParam(required = false) String collectionId,
			@RequestParam(required = false) ContentType contentType,
			@RequestParam(required = false) AccessTier accessTier, @RequestParam(required = false) String q,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return catalogueItems.list(adminScope.currentPublisherScope(), publisherId, collectionId, contentType,
				accessTier, q, page, size);
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

}
