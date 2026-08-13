package com.tf.reader.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.AdminLoginResponse;
import com.tf.reader.admin.dto.AdminProfileResponse;
import com.tf.reader.admin.dto.LoginRequest;
import com.tf.reader.admin.dto.RefreshRequest;
import com.tf.reader.admin.dto.TokenPair;
import com.tf.reader.admin.service.AdminAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(path = "/api/admin/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Admin authentication", description = "Login, token refresh, logout and current-admin lookup.")
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	public AdminAuthController(AdminAuthService adminAuthService) {
		this.adminAuthService = adminAuthService;
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Console sign in",
			description = "Verifies the password and opens a session. Invalid credentials and non-active "
					+ "accounts return the same 401, so responses cannot be used to discover which "
					+ "email addresses exist.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "An access token and a refresh token."),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()),
			@ApiResponse(responseCode = "401", description = "Authentication failed.",
					content = @Content()) })
	public AdminLoginResponse login(@Valid @RequestBody LoginRequest request) {
		return this.adminAuthService.login(request.email(), request.password());
	}

	@PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Trade a refresh token for a new access token",
			description = "Issues a new access token and rotates the refresh token. The presented refresh "
					+ "token stops working immediately. Replaying an already-rotated token revokes the "
					+ "whole session. Access tokens are never accepted here. Returns no user: refreshing "
					+ "proves nothing new about who the caller is.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "A new pair. The one you sent is now dead."),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()),
			@ApiResponse(responseCode = "401",
					description = "Unknown, expired, already used or revoked. All four are the same answer, "
							+ "and the only cure is signing in again.",
					content = @Content()) })
	public TokenPair refresh(@Valid @RequestBody RefreshRequest request) {
		return this.adminAuthService.refresh(request.refreshToken());
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "adminToken")
	@Operation(summary = "Who am I, and what may I do",
			description = "Returns the authenticated admin's profile. Never includes the password hash.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The signed in operator."),
			@ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked access token.",
					content = @Content()),
			@ApiResponse(responseCode = "403", description = "Authenticated but not permitted.",
					content = @Content()) })
	public AdminProfileResponse me(@AuthenticationPrincipal Jwt accessToken) {
		return AdminProfileResponse.from(this.adminAuthService.requireAdmin(accessToken.getSubject()));
	}

	@PostMapping(path = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Revoke a refresh token",
			description = "Marks the session revoked so the refresh token can never be exchanged again. "
					+ "Always 204, even for a token that never existed, so nobody can probe which ones "
					+ "are live. The console must drop its access token on its own side too.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Revoked, or was never valid. Same answer either way.",
					content = @Content()),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()) })
	public void logout(@Valid @RequestBody RefreshRequest request) {
		this.adminAuthService.logout(request.refreshToken());
	}

}
