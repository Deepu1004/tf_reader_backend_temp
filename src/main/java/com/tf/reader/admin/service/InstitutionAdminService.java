package com.tf.reader.admin.service;

import com.tf.reader.admin.dto.AdminInstitution;
import com.tf.reader.admin.dto.InstitutionSummary;
import com.tf.reader.admin.dto.InstitutionWrite;
import com.tf.reader.admin.dto.SignInWrite;
import com.tf.reader.admin.entity.AdminRole;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.dto.SignInView;
import com.tf.reader.catalogue.entity.Branding;
import com.tf.reader.catalogue.entity.EntitlementStatus;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.repository.EntitlementRepository;
import com.tf.reader.catalogue.repository.InstitutionAdminRepository;
import com.tf.reader.catalogue.repository.InstitutionRepository;
import com.tf.reader.catalogue.service.CatalogueUrlBuilder;
import com.tf.reader.catalogue.service.CatalogueVersionBumper;
import com.tf.reader.catalogue.service.InstitutionCatalogueSummaryService;
import com.tf.reader.common.audit.AdminAuditWriter;
import com.tf.reader.common.audit.AuditLog;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageResponse;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles listing, creating, reading, updating and suspending institutions from the admin console.
 * Every rule about what an operator can see or change lives here, not in the controller, so no
 * future endpoint can accidentally skip a check.
 */
@Service
public class InstitutionAdminService {

    private final InstitutionRepository institutions;
    private final InstitutionAdminRepository adminRepository;
    private final EntitlementRepository entitlements;
    private final InstitutionCatalogueSummaryService summaryService;
    private final CatalogueUrlBuilder catalogueUrlBuilder;
    private final AdminAuditWriter auditWriter;
    private final CatalogueVersionBumper versionBumper;
    private final AdminScopeAuthorizer adminScope;

    public InstitutionAdminService(
            InstitutionRepository institutions,
            InstitutionAdminRepository adminRepository,
            EntitlementRepository entitlements,
            InstitutionCatalogueSummaryService summaryService,
            CatalogueUrlBuilder catalogueUrlBuilder,
            AdminAuditWriter auditWriter,
            CatalogueVersionBumper versionBumper,
            AdminScopeAuthorizer adminScope) {
        this.institutions = institutions;
        this.adminRepository = adminRepository;
        this.entitlements = entitlements;
        this.summaryService = summaryService;
        this.catalogueUrlBuilder = catalogueUrlBuilder;
        this.auditWriter = auditWriter;
        this.versionBumper = versionBumper;
        this.adminScope = adminScope;
    }

    // -------------------------------------------------------------------------------------- list

    /**
     * Returns institutions of any status, so a suspended one still shows up here. When
     * institutionIdScope is non-null, the result is restricted to that one institution — the
     * caller has already decided who gets to see everything and who does not; this method just
     * applies whatever scope it is given, in the query, not by filtering afterwards.
     */
    public PageResponse<AdminInstitution> list(
            String q, RecordStatus status, String institutionIdScope, int page, int size) {
        AdminRole role = adminScope.currentRole();
        if (role != AdminRole.SUPER_ADMIN && role != AdminRole.INSTITUTION_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE,
                    "This operation requires SUPER_ADMIN or INSTITUTION_ADMIN.");
        }
        InstitutionAdminRepository.Results results =
                adminRepository.search(q, status, institutionIdScope, page, size);
        List<AdminInstitution> items = results.items().stream().map(this::toAdminInstitution).toList();
        return new PageResponse<>(items, page, size, results.total());
    }

    // ------------------------------------------------------------------------------------ create

    public AdminInstitution create(InstitutionWrite request) {
        adminScope.requireSuperAdmin();
        InstitutionWrite validated = request.validate();

        institutions
                .findByCode(validated.code())
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.CODE_TAKEN, "That code is already used");
                });

        Instant now = Instant.now();
        Institution created =
                new Institution(
                        newId(),
                        validated.code(),
                        validated.name(),
                        validated.type(),
                        validated.country(),
                        validated.city(),
                        toBrandingEntity(validated.branding()),
                        toSignInEntity(validated.signIn()),
                        RecordStatus.ACTIVE,
                        1L,
                        now,
                        now);
        // Email domains are accepted and checked, then set aside — nothing stores them yet.

        Institution saved = institutions.save(created);

        auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.CREATE, "INSTITUTION", saved.getId(),
                Map.of(), fieldsOf(saved));

        return toAdminInstitution(saved);
    }

    // --------------------------------------------------------------------------------------- get

    public AdminInstitution get(String institutionId) {
        if (!adminScope.canAccessInstitution(institutionId)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to access this institution");
        }
        return toAdminInstitution(findOrThrow(institutionId));
    }

    // ------------------------------------------------------------------------------------ update

    /** Replaces the editable fields on an institution. Does not change its status or version. */
    public AdminInstitution update(String institutionId, InstitutionWrite request) {
        adminScope.requireSuperAdmin();
        InstitutionWrite validated = request.validate();
        Institution existing = findOrThrow(institutionId);

        if (!validated.code().equals(existing.getCode())) {
            institutions
                    .findByCode(validated.code())
                    .filter(other -> !other.getId().equals(institutionId))
                    .ifPresent(other -> {
                        throw new ApiException(ErrorCode.CODE_TAKEN, "That code is already used");
                    });
        }

        Map<String, Object> beforeSnapshot = fieldsOf(existing);

        existing.setCode(validated.code());
        existing.setName(validated.name());
        existing.setType(validated.type());
        existing.setCountry(validated.country());
        existing.setCity(validated.city());
        existing.setBranding(toBrandingEntity(validated.branding()));
        existing.setSignIn(toSignInEntity(validated.signIn()));
        existing.setUpdatedAt(Instant.now());

        Institution saved = institutions.save(existing);

        // Only record what actually changed, so the audit trail stays readable.
        Map<String, Object> before = new HashMap<>();
        Map<String, Object> after = new HashMap<>();
        Map<String, Object> afterSnapshot = fieldsOf(saved);
        for (String key : beforeSnapshot.keySet()) {
            if (!Objects.equals(beforeSnapshot.get(key), afterSnapshot.get(key))) {
                before.put(key, beforeSnapshot.get(key));
                after.put(key, afterSnapshot.get(key));
            }
        }

        auditWriter.record(adminScope.currentAdminId(), AuditLog.Action.UPDATE, "INSTITUTION", institutionId, before,
                after);

        return toAdminInstitution(saved);
    }

    // ---------------------------------------------------------------------------------- status

    /** Activates or suspends an institution and refreshes its catalogue version. */
    public AdminInstitution setStatus(String institutionId, RecordStatus newStatus, String reason) {
        adminScope.requireSuperAdmin();
        Institution existing = findOrThrow(institutionId);
        RecordStatus oldStatus = existing.getStatus();

        existing.setStatus(newStatus);
        existing.setUpdatedAt(Instant.now());
        Institution saved = institutions.save(existing);

        Map<String, Object> after = new HashMap<>();
        after.put("status", newStatus);
        if (reason != null) {
            after.put("reason", reason);
        }
        auditWriter.record(adminScope.currentAdminId(),
                AuditLog.Action.STATUS, "INSTITUTION", institutionId, Map.of("status", oldStatus), after);

        // Suspending or reactivating an institution changes what its own members can see, so it
        // refreshes its own version rather than looking anything else up.
        versionBumper.bump(CatalogueVersionBumper.Scope.INSTITUTION, institutionId);

        return toAdminInstitution(saved);
    }

    // ------------------------------------------------------------------------------------ mapping

    private Institution findOrThrow(String institutionId) {
        return adminRepository
                .findById(institutionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No such institution"));
    }

    private AdminInstitution toAdminInstitution(Institution i) {
        long entitlementCount =
                entitlements.findByInstitutionIdAndStatus(i.getId(), EntitlementStatus.ACTIVE).size();
        long accessibleItemCount = summaryService.countAccessibleItems(i.getId());
        String feedUrl = catalogueUrlBuilder.catalogueUrlFor(i.getId());

        return new AdminInstitution(
                i.getId(),
                i.getCode(),
                i.getName(),
                i.getCountry(),
                i.getCity(),
                toBrandingView(i),
                toSignInView(i),
                feedUrl,
                i.getType(),
                List.of(), // no email domains stored yet
                i.getStatus(),
                i.getCatalogueVersion(),
                new InstitutionSummary(entitlementCount, accessibleItemCount, feedUrl));
    }

    private BrandingView toBrandingView(Institution i) {
        return i.getBranding() == null
                ? null
                : new BrandingView(i.getBranding().getLogoUrl(), i.getBranding().getPrimaryColor());
    }

    private SignInView toSignInView(Institution i) {
        return i.getSignIn() == null
                ? null
                : new SignInView(i.getSignIn().getMethod(), i.getSignIn().getIdpHint());
    }

    private Branding toBrandingEntity(BrandingView v) {
        return v == null ? null : new Branding(v.logoUrl(), v.primaryColor());
    }

    private Institution.SignIn toSignInEntity(SignInWrite w) {
        return w == null ? null : new Institution.SignIn(w.method(), w.idpHint());
    }

    /**
     * A snapshot of every field a write can actually change, used to work out what to put in an
     * audit row. Branding and sign-in are flattened to their own plain values rather than kept as
     * entity objects, so the before/after diff below compares strings and never depends on those
     * entity classes having their own equals().
     */
    private Map<String, Object> fieldsOf(Institution i) {
        Map<String, Object> m = new HashMap<>();
        m.put("code", i.getCode());
        m.put("name", i.getName());
        m.put("type", i.getType());
        m.put("country", i.getCountry());
        m.put("city", i.getCity());
        m.put("status", i.getStatus());
        // Mongo rejects a map key containing a dot, so these stay dot-free even though they read
        // as nested fields.
        m.put("brandingLogoUrl", i.getBranding() == null ? null : i.getBranding().getLogoUrl());
        m.put("brandingPrimaryColor", i.getBranding() == null ? null : i.getBranding().getPrimaryColor());
        m.put("signInMethod", i.getSignIn() == null ? null : i.getSignIn().getMethod());
        m.put("signInIdpHint", i.getSignIn() == null ? null : i.getSignIn().getIdpHint());
        return m;
    }

    private static String newId() {
        return "inst_" + UUID.randomUUID().toString().substring(0, 8);
    }
}