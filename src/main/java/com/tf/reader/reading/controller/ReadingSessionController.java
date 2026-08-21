package com.tf.reader.reading.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.catalogue.api.SubjectRef;
import com.tf.reader.reading.dto.ReadingSessionRequest;
import com.tf.reader.reading.dto.ReadingSessionResponse;
import com.tf.reader.reading.service.ReadBrokerService;

import jakarta.validation.Valid;

/**
 * The single inbound door for reading and downloading (POST /api/v1/reading-sessions).
 *
 * <p>Deliberately thin: reads identity once from the authenticated token principal and passes
 * {@link SubjectRef} down as a parameter. No business logic or entitlement decisions live here.
 */
@RestController
@RequestMapping("/api/v1/reading-sessions")
public class ReadingSessionController {

	private final ReadBrokerService broker;

	public ReadingSessionController(ReadBrokerService broker) {
		this.broker = broker;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReadingSessionResponse open(
			@Valid @RequestBody ReadingSessionRequest request,
			@AuthenticationPrincipal CurrentUser caller) {

		String userId = caller != null ? caller.userId() : null;
		String institutionId = caller != null ? caller.institutionId() : null;
		SubjectRef subject = new SubjectRef(userId, institutionId);

		return broker.open(subject, request);
	}
}
