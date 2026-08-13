package com.tf.reader.admin.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.AdminProfileResponse;
import com.tf.reader.admin.dto.LoginRequest;
import com.tf.reader.admin.dto.LogoutResponse;
import com.tf.reader.admin.dto.RefreshRequest;
import com.tf.reader.admin.dto.TokenResponse;
import com.tf.reader.admin.service.AdminAuthService;
import com.tf.reader.common.security.TokenClaims;

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
	@Operation(summary = "Log in as an admin",
			description = "Verifies the password and opens a session. Invalid credentials and non-active "
					+ "accounts return the same 401, so responses cannot be used to discover which "
					+ "email addresses exist.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Authenticated; access and refresh tokens issued."),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()),
			@ApiResponse(responseCode = "401", description = "Authentication failed.",
					content = @Content()) })
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return this.adminAuthService.login(request.email(), request.password());
	}

	@PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Exchange a refresh token",
			description = "Issues a new access token and rotates the refresh token. The presented refresh "
					+ "token stops working immediately. Replaying an already-rotated token revokes the "
					+ "whole session. Access tokens are never accepted here.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "New access and refresh tokens issued."),
			@ApiResponse(responseCode = "400", description = "Malformed or incomplete request body.",
					content = @Content()),
			@ApiResponse(responseCode = "401",
					description = "Refresh token is invalid, expired, revoked, already used, or not a refresh token.",
					content = @Content()) })
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return this.adminAuthService.refresh(request.refreshToken());
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "adminBearerAuth")
	@Operation(summary = "Get the current admin",
			description = "Returns the authenticated admin's profile. Never includes the password hash.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The authenticated admin."),
			@ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked access token.",
					content = @Content()),
			@ApiResponse(responseCode = "403", description = "Authenticated but not permitted.",
					content = @Content()) })
	public AdminProfileResponse me(@AuthenticationPrincipal Jwt accessToken) {
		return AdminProfileResponse.from(this.adminAuthService.requireAdmin(accessToken.getSubject()));
	}

	@PostMapping("/logout")
	@SecurityRequirement(name = "adminBearerAuth")
	@Operation(summary = "Log out",
			description = "Revokes the session identified by the access token. The refresh token can no "
					+ "longer be exchanged and the access token stops authorizing admin APIs on the next "
					+ "request. Repeating the call is safe.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Session revoked, or already revoked."),
			@ApiResponse(responseCode = "401", description = "Missing, invalid, expired or revoked access token.",
					content = @Content()),
			@ApiResponse(responseCode = "403", description = "Authenticated but not permitted.",
					content = @Content()) })
	public LogoutResponse logout(@AuthenticationPrincipal Jwt accessToken) {
		return new LogoutResponse(
				this.adminAuthService.logout(accessToken.getClaimAsString(TokenClaims.SESSION_ID)));
	}

}
