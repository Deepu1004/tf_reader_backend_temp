package com.tf.reader.admin.controller;

import com.tf.reader.admin.dto.AdminInstitution;
import com.tf.reader.admin.dto.InstitutionWrite;
import com.tf.reader.admin.dto.StatusChange;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminRoles;
import com.tf.reader.admin.service.InstitutionAdminService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.common.security.TokenClaims;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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

/**
 * The admin-side institution endpoints: list, create, read, update, and set status. Only a super
 * admin may create, update, or change status; a super admin or that institution's own admin may
 * read it; a publisher admin has no access here at all.
 */
@RestController
@RequestMapping("/api/admin/v1/institutions")
public class InstitutionAdminController {

    private final InstitutionAdminService institutions;

    public InstitutionAdminController(InstitutionAdminService institutions) {
        this.institutions = institutions;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'INSTITUTION_ADMIN')")
    public PageResponse<AdminInstitution> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int resolvedPage = page == null ? 0 : validatedPage(page);
        int resolvedSize = size == null ? 20 : validatedSize(size);
        // Role admission alone is not enough here: an institution admin passes the role check but
        // must still see only their own institution, filtered in the query itself, not afterwards.
        String institutionScope = currentInstitutionScope();
        return institutions.list(blankToNull(q), status, institutionScope, resolvedPage, resolvedSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminInstitution create(@RequestBody InstitutionWrite request) {
        return institutions.create(request);
    }

    @GetMapping("/{institutionId}")
    @PreAuthorize("@adminScope.canAccessInstitution(#institutionId)")
    public AdminInstitution get(@PathVariable String institutionId) {
        return institutions.get(institutionId);
    }

    @PutMapping("/{institutionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminInstitution update(
            @PathVariable String institutionId, @RequestBody InstitutionWrite request) {
        return institutions.update(institutionId, request);
    }

    @PatchMapping("/{institutionId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public AdminInstitution setStatus(
            @PathVariable String institutionId, @RequestBody StatusChange request) {
        return institutions.setStatus(institutionId, request.status(), request.reason());
    }

    // ------------------------------------------------------------------------------- normalisation

    /**
     * Null means unfiltered (a super admin). Anything else is the one institution id the list
     * should be restricted to. Reads the role and scope straight off the JWT, the same way
     * {@code AdminScopeAuthorizer} does for the {@code {institutionId}} routes — that class only
     * checks a caller against one already-known target id, and list has no id in the path to check
     * against, so this reads the claims directly instead of going through it. Fails closed: no
     * token, no recognised role, or a missing scope claim all narrow to nothing rather than to
     * everything.
     */
    private static String currentInstitutionScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return "no-institution-claim";
        }

        AdminRole role = AdminRoles.parse(jwt.getClaimAsString(TokenClaims.ROLE));
        if (role == AdminRole.SUPER_ADMIN) {
            return null;
        }
        if (role != AdminRole.INSTITUTION_ADMIN) {
            return "no-institution-claim";
        }

        String scope = jwt.getClaimAsString(TokenClaims.SCOPE_INSTITUTION_ID);
        return (scope == null || scope.isBlank()) ? "no-institution-claim" : scope;
    }

    private static Integer validatedPage(int page) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be zero or greater");
        }
        return page;
    }

    private static Integer validatedSize(int size) {
        if (size < 1 || size > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and 100");
        }
        return size;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}