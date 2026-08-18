package com.tf.reader.loan.controller;

import java.time.Clock;
import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.auth.model.CurrentUser;
import com.tf.reader.loan.dto.BorrowRequest;
import com.tf.reader.loan.dto.LoanResponse;
import com.tf.reader.loan.service.BorrowService;
import com.tf.reader.loan.service.BorrowService.BorrowResult;

/**
 * HTTP surface for the loan module: borrow, list, return.
 * Validation happens here with {@code @Valid}; business logic lives in the services.
 */
@RestController
@RequestMapping("/api/v1/loans")
public class LoanController {

	private final BorrowService borrowService;
	private final Clock clock;

	LoanController(BorrowService borrowService, Clock clock) {
		this.borrowService = borrowService;
		this.clock = clock;
	}

	/**
	 * Take possession of a title (or validate an existing one).
	 *
	 * <p>Returns {@code 200} when the reader already holds an active loan,
	 * {@code 201} when a new loan was created. The client must handle both —
	 * a double-tap is the ordinary case, not an exceptional one.
	 */
	@PostMapping
	ResponseEntity<LoanResponse> borrow(
			@Valid @RequestBody BorrowRequest request,
			@AuthenticationPrincipal CurrentUser currentUser) {

		BorrowResult result = borrowService.borrow(
				currentUser.userId(),
				currentUser.institutionId(),
				request.itemId());

		Instant serverTime = Instant.now(clock);
		LoanResponse body = LoanResponse.from(result.loan(), serverTime);

		return result.created()
				? ResponseEntity.status(HttpStatus.CREATED).body(body)
				: ResponseEntity.ok(body);
	}
}
