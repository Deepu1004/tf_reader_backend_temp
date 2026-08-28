package com.tf.reader.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tf.reader.admin.dto.AssetFormat;
import com.tf.reader.admin.dto.CatalogueItemView;
import com.tf.reader.admin.dto.CatalogueItemWrite;
import com.tf.reader.admin.dto.IngestStatus;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.CatalogueItemAdminService;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.ingest.service.IngestService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/admin/v1/catalogue-items")
public class CatalogueItemAdminController {

	private final CatalogueItemAdminService catalogueItems;
	private final AdminScopeAuthorizer adminScope;
	private final IngestService ingestItems;

	public CatalogueItemAdminController(CatalogueItemAdminService catalogueItems, AdminScopeAuthorizer adminScope,
			IngestService ingestItems) {
		this.catalogueItems = catalogueItems;
		this.adminScope = adminScope;
		this.ingestItems = ingestItems;
	}

	@GetMapping
	public PageResponse<CatalogueItemView> list(@RequestParam(required = false) String publisherId,
			@RequestParam(required = false) String collectionId,
			@RequestParam(required = false) ContentType contentType,
			@RequestParam(required = false) AccessTier accessTier, @RequestParam(required = false) String q,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String institutionId) {

		String publisherIdScope = adminScope.currentRole() == AdminRole.PUBLISHER_ADMIN
				? adminScope.currentPublisherScope()
				: null;
		return catalogueItems.list(publisherIdScope, publisherId, collectionId, contentType, accessTier, q, page,
				size, institutionId);
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

	@PostMapping(value = "/{itemId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public IngestStatus uploadContent(@PathVariable String itemId, @RequestParam("file") MultipartFile file,
			@RequestParam("format") AssetFormat format) {
		return ingestItems.accept(itemId, file, format);
	}

	@GetMapping("/{itemId}/ingest-status")
	public IngestStatus ingestStatus(@PathVariable String itemId) {
		return ingestItems.getStatus(itemId);
	}

}
