package com.tf.reader.catalogue.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.catalogue.dto.BatchItemsRequest;
import com.tf.reader.catalogue.dto.BatchItemsResponse;
import com.tf.reader.catalogue.service.CatalogueBatchService;

import jakarta.validation.Valid;

/**
 * {@code POST /api/v1/catalogue/items:batch} - turns up to 100 item ids into details in one
 * call, for a screen that only holds ids (a loan history, a shelf).
 *
 * <p>Reads identity via {@code @AuthenticationPrincipal CurrentUser}, the same way
 * {@code ReadingSessionController} does. This used to resolve to null on this chain -
 * {@code common.security.SecurityConfig}'s {@code appApiFilterChain} registered no
 * {@code jwtAuthenticationConverter}, leaving the principal as a raw {@code Jwt} - but that
 * chain now wires {@code CurrentUserJwtConverter} too, so {@code CurrentUser} is populated here
 * like everywhere else. Both fields are still read defensively: the resource server rejects an
 * unauthenticated request before this method ever runs, but a caller with a token that carries
 * no {@code institutionId} claim (an individual subscriber) still reaches here with one field
 * null rather than both.
 *
 * <p>HTTP only. Every rule about who may see what lives in {@link CatalogueBatchService}.
 */
@RestController
@RequestMapping("/api/v1/catalogue/items:batch")
public class CatalogueBatchController {

	private final CatalogueBatchService batchService;

	public CatalogueBatchController(CatalogueBatchService batchService) {
		this.batchService = batchService;
	}

	@PostMapping
	public BatchItemsResponse batch(@Valid @RequestBody BatchItemsRequest request,
			@AuthenticationPrincipal CurrentUser caller) {
		SubjectRef subject = new SubjectRef(
				caller != null ? caller.userId() : null,
				caller != null ? caller.institutionId() : null);

		return batchService.batch(subject, request);
	}

}
