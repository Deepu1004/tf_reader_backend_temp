package com.tf.reader.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.PublisherView;
import com.tf.reader.admin.dto.PublisherWrite;
import com.tf.reader.admin.dto.StatusChange;
import com.tf.reader.admin.service.PublisherAdminService;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageQuery;
import com.tf.reader.common.page.PageResponse;

import jakarta.validation.Valid;

/**
 * Five publisher admin endpoints.
 *
 * <pre>
 *   GET    /api/admin/v1/publishers                    list (any authenticated admin)
 *   POST   /api/admin/v1/publishers                    create, 201
 *   GET    /api/admin/v1/publishers/{publisherId}       get
 *   PUT    /api/admin/v1/publishers/{publisherId}       update
 *   PATCH  /api/admin/v1/publishers/{publisherId}/status  change status
 * </pre>
 *
 * <p>
 * The list endpoint is restricted to {@code SUPER_ADMIN} — a scoped admin can
 * only reach their own publisher by id, not enumerate all publishers. Every
 * other endpoint is guarded by
 * {@link com.tf.reader.admin.security.AdminScopeAuthorizer#canAccessPublisher}.
 *
 * <p>
 * HTTP only. All rules, including who may call what, live in
 * {@link PublisherAdminService} - a controller-only check is bypassed the
 * moment a second entry point calls the same service.
 */
@RestController("publisherAdminController")
@RequestMapping("/api/admin/v1/publishers")
public class PublisherAdminController {

	private final PublisherAdminService publishers;

	public PublisherAdminController(PublisherAdminService publishers) {
		this.publishers = publishers;
	}

	@GetMapping
	public PageResponse<PublisherView> list(@RequestParam(required = false) String q,
			@RequestParam(required = false) RecordStatus status, PageQuery pageQuery) {
		return publishers.list(q, status, pageQuery);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PublisherView create(@Valid @RequestBody PublisherWrite body) {
		return publishers.create(body);
	}

	@GetMapping("/{publisherId}")
	public PublisherView get(@PathVariable String publisherId) {
		return publishers.get(publisherId);
	}

	@PutMapping("/{publisherId}")
	public PublisherView update(@PathVariable String publisherId, @Valid @RequestBody PublisherWrite body) {
		return publishers.update(publisherId, body);
	}

	@PatchMapping("/{publisherId}/status")
	public PublisherView changeStatus(@PathVariable String publisherId, @Valid @RequestBody StatusChange body) {
		return publishers.changeStatus(publisherId, body);
	}
}
