package com.tf.reader.admin.controller;

import com.tf.reader.admin.dto.AdminInstitution;
import com.tf.reader.admin.dto.InstitutionWrite;
import com.tf.reader.admin.dto.StatusChange;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.InstitutionAdminService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageResponse;

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

import jakarta.validation.Valid;

/**
 * The admin-side institution endpoints: list, create, read, update, and set status. Only a super
 * admin may create, update, or change status; a super admin or that institution's own admin may
 * read it; a publisher admin has no access here at all.
 *
 * <p>Who may call what is decided in {@link InstitutionAdminService}, not here - a controller-only
 * check is bypassed the moment a second entry point calls the same service.
 */
@RestController
@RequestMapping("/api/admin/v1/institutions")
public class InstitutionAdminController {

    private final InstitutionAdminService institutions;
    private final AdminScopeAuthorizer adminScope;

    public InstitutionAdminController(InstitutionAdminService institutions, AdminScopeAuthorizer adminScope) {
        this.institutions = institutions;
        this.adminScope = adminScope;
    }

    @GetMapping
    public PageResponse<AdminInstitution> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RecordStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        int resolvedPage = page == null ? 0 : validatedPage(page);
        int resolvedSize = size == null ? 20 : validatedSize(size);
        // Role admission alone is not enough here: an institution admin passes the role check but
        // must still see only their own institution, filtered in the query itself, not afterwards.
        String institutionScope = adminScope.currentInstitutionScope();
        return institutions.list(blankToNull(q), status, institutionScope, resolvedPage, resolvedSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminInstitution create(@RequestBody InstitutionWrite request) {
        return institutions.create(request);
    }

    @GetMapping("/{institutionId}")
    public AdminInstitution get(@PathVariable String institutionId) {
        return institutions.get(institutionId);
    }

    @PutMapping("/{institutionId}")
    public AdminInstitution update(
            @PathVariable String institutionId, @RequestBody InstitutionWrite request) {
        return institutions.update(institutionId, request);
    }

    @PatchMapping("/{institutionId}/status")
    public AdminInstitution setStatus(
            @PathVariable String institutionId, @Valid @RequestBody StatusChange request) {
        return institutions.setStatus(institutionId, request.status(), request.reason());
    }

    // ------------------------------------------------------------------------------- normalisation

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
