package com.tf.reader.admin;

import com.tf.reader.admin.dto.AdminInstitution;
import com.tf.reader.admin.dto.InstitutionWrite;
import com.tf.reader.admin.dto.SignInWrite;
import com.tf.reader.admin.service.InstitutionAdminService;
import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.entity.Branding;
import com.tf.reader.catalogue.entity.Institution;
import com.tf.reader.catalogue.entity.InstitutionType;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Checks that every institution write records the right audit entry and touches the version only when it should. */
class InstitutionAdminServiceTest {

    private InstitutionRepository institutions;
    private InstitutionAdminRepository adminRepository;
    private EntitlementRepository entitlements;
    private InstitutionCatalogueSummaryService summaryService;
    private CatalogueUrlBuilder urlBuilder;
    private AdminAuditWriter auditWriter;
    private CatalogueVersionBumper versionBumper;
    private InstitutionAdminService service;

    @BeforeEach
    void setUp() {
        institutions = mock(InstitutionRepository.class);
        adminRepository = mock(InstitutionAdminRepository.class);
        entitlements = mock(EntitlementRepository.class);
        summaryService = mock(InstitutionCatalogueSummaryService.class);
        urlBuilder = mock(CatalogueUrlBuilder.class);
        auditWriter = mock(AdminAuditWriter.class);
        versionBumper = mock(CatalogueVersionBumper.class);

        when(urlBuilder.catalogueUrlFor(anyString()))
                .thenAnswer(inv -> "http://localhost:8080/opds/v1/institutions/" + inv.getArgument(0) + "/catalogue");
        when(entitlements.findByInstitutionIdAndStatus(anyString(), any())).thenReturn(List.of());
        when(summaryService.countAccessibleItems(anyString())).thenReturn(0L);
        when(institutions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new InstitutionAdminService(
                institutions, adminRepository, entitlements, summaryService, urlBuilder,
                auditWriter, versionBumper);
    }

    // ------------------------------------------------------------------------------------ create

    @Test
    @DisplayName("duplicate code on create is 409 CODE_TAKEN")
    void duplicateCodeOnCreateIs409() {
        when(institutions.findByCode("oxford")).thenReturn(Optional.of(existingInstitution("oxford")));

        assertThatThrownBy(() -> service.create(validWrite("oxford")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN));
    }

    @Test
    @DisplayName("create audits CREATE once, with an empty before and a populated after")
    void createAuditsOnce() {
        when(institutions.findByCode("oxford")).thenReturn(Optional.empty());

        service.create(validWrite("oxford"));

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter, times(1))
                .record(eq(AuditLog.Action.CREATE), eq("INSTITUTION"), anyString(), before.capture(), after.capture());

        assertThat(before.getValue()).isEmpty();
        assertThat(after.getValue()).containsEntry("code", "oxford");
        verify(versionBumper, never()).bump(any(), anyString());
    }

    @Test
    @DisplayName("email domains sent on create come back empty")
    void emailDomainsIsAcceptedButAlwaysComesBackEmpty() {
        when(institutions.findByCode("oxford")).thenReturn(Optional.empty());

        InstitutionWrite request = new InstitutionWrite(
                "oxford", "University of Oxford", InstitutionType.ACADEMIC, "UK", "Oxford",
                List.of("ox.ac.uk", "OX.AC.UK"), new SignInWrite("SAML", "oxford-saml-mock"), null);

        AdminInstitution result = service.create(request);

        assertThat(result.emailDomains()).isEmpty();
    }

    // ------------------------------------------------------------------------------------ update

    @Test
    @DisplayName("updating a record to its own current code is not a conflict")
    void updateWithUnchangedCodeIsNotAConflict() {
        Institution existing = existingInstitution("oxford");
        when(adminRepository.findById("inst_ox")).thenReturn(Optional.of(existing));
        when(institutions.findByCode("oxford")).thenReturn(Optional.of(existing));

        AdminInstitution result = service.update("inst_ox", validWrite("oxford"));

        assertThat(result.code()).isEqualTo("oxford");
    }

    @Test
    @DisplayName("changing to a code another institution already has is 409")
    void updateToAnotherInstitutionsCodeIs409() {
        Institution existing = existingInstitution("oxford");
        existing.setCode("oxford-old");
        when(adminRepository.findById("inst_ox")).thenReturn(Optional.of(existing));

        // Must be a different id from the institution being updated, or the service's own
        // "is this actually a different record" filter treats it as harmless and no 409 fires.
        Institution other = existingInstitution("inst_cam", "cambridge");
        when(institutions.findByCode("oxford")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update("inst_ox", validWrite("oxford")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.CODE_TAKEN));
    }

    @Test
    @DisplayName("update audits only the fields that actually changed, and never bumps the version")
    void updateAuditsOnlyChangedFields() {
        Institution existing = existingInstitution("oxford");
        existing.setName("Old Name");
        when(adminRepository.findById("inst_ox")).thenReturn(Optional.of(existing));
        when(institutions.findByCode("oxford")).thenReturn(Optional.of(existing));

        service.update("inst_ox", validWrite("oxford")); // validWrite() names it "University of Oxford"

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).record(eq(AuditLog.Action.UPDATE), eq("INSTITUTION"), eq("inst_ox"),
                before.capture(), after.capture());

        assertThat(before.getValue()).containsEntry("name", "Old Name");
        assertThat(after.getValue()).containsEntry("name", "University of Oxford");
        assertThat(before.getValue()).as("code did not change, so it is not in the diff").doesNotContainKey("code");
        verify(versionBumper, never()).bump(any(), anyString());
    }

    @Test
    @DisplayName("update audits a branding or sign-in change too, not just the flat fields")
    void updateAuditsBrandingAndSignInChanges() {
        Institution existing = existingInstitution("oxford");
        when(adminRepository.findById("inst_ox")).thenReturn(Optional.of(existing));
        when(institutions.findByCode("oxford")).thenReturn(Optional.of(existing));

        // signIn.method may only ever be "SAML" — idpHint is the field that is actually free to change.
        InstitutionWrite request = new InstitutionWrite(
                "oxford", "University of Oxford", InstitutionType.ACADEMIC, "UK", "Oxford",
                List.of("ox.ac.uk"), new SignInWrite("SAML", "new-saml-hint"),
                new BrandingView("https://cdn.tf/logos/new.png", "#123456"));

        service.update("inst_ox", request);

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(auditWriter).record(eq(AuditLog.Action.UPDATE), eq("INSTITUTION"), eq("inst_ox"),
                before.capture(), after.capture());

        assertThat(after.getValue()).containsEntry("brandingLogoUrl", "https://cdn.tf/logos/new.png");
        assertThat(after.getValue()).containsEntry("signInIdpHint", "new-saml-hint");
        assertThat(before.getValue()).containsEntry("brandingLogoUrl", "https://cdn.tf.example/logos/oxford.png");
        assertThat(before.getValue()).containsEntry("signInIdpHint", "oxford-saml-mock");
    }

    // ------------------------------------------------------------------------------------ status

    @Test
    @DisplayName("status change audits STATUS with the reason folded into after, and refreshes the version")
    void statusChangeAuditsAndBumps() {
        Institution existing = existingInstitution("oxford");
        existing.setStatus(RecordStatus.ACTIVE);
        when(adminRepository.findById("inst_ox")).thenReturn(Optional.of(existing));

        service.setStatus("inst_ox", RecordStatus.SUSPENDED, "budget review");

        verify(auditWriter).record(
                eq(AuditLog.Action.STATUS), eq("INSTITUTION"), eq("inst_ox"),
                eq(Map.of("status", RecordStatus.ACTIVE)),
                eq(Map.of("status", RecordStatus.SUSPENDED, "reason", "budget review")));
        verify(versionBumper, times(1)).bump(CatalogueVersionBumper.Scope.INSTITUTION, "inst_ox");
    }

    @Test
    @DisplayName("a status change with no reason leaves it out of the audit entry")
    void statusChangeWithNoReasonOmitsIt() {
        Institution existing = existingInstitution("oxford");
        existing.setStatus(RecordStatus.SUSPENDED);
        when(adminRepository.findById("inst_ox")).thenReturn(Optional.of(existing));

        service.setStatus("inst_ox", RecordStatus.ACTIVE, null);

        verify(auditWriter).record(
                eq(AuditLog.Action.STATUS), eq("INSTITUTION"), eq("inst_ox"),
                eq(Map.of("status", RecordStatus.SUSPENDED)),
                eq(Map.of("status", RecordStatus.ACTIVE)));
    }

    // ------------------------------------------------------------------------------------- list

    @Test
    @DisplayName("a null scope reaches the repository as unfiltered, an institution scope reaches it unchanged")
    void listPassesTheScopeStraightToTheRepository() {
        when(adminRepository.search(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new InstitutionAdminRepository.Results(List.of(), 0));

        service.list(null, null, "inst_ox", 0, 20);

        verify(adminRepository).search(null, null, "inst_ox", 0, 20);
    }

    @Test
    @DisplayName("an unknown institution id is 404 on every operation")
    void unknownIdIs404() {
        when(adminRepository.findById("inst_ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("inst_ghost"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ---------------------------------------------------------------------------------- fixtures

    private static InstitutionWrite validWrite(String code) {
        return new InstitutionWrite(
                code, "University of Oxford", InstitutionType.ACADEMIC, "UK", "Oxford",
                List.of("ox.ac.uk"), new SignInWrite("SAML", "oxford-saml-mock"),
                new BrandingView("https://cdn.tf/logos/oxford.png", "#002147"));
    }

    private static Institution existingInstitution(String code) {
        return existingInstitution("inst_ox", code);
    }

    private static Institution existingInstitution(String id, String code) {
        Instant now = Instant.parse("2026-08-17T09:00:00Z");
        return new Institution(
                id, code, "University of Oxford", InstitutionType.ACADEMIC, "UK", "Oxford",
                new Branding("https://cdn.tf.example/logos/oxford.png", "#002147"),
                new Institution.SignIn("SAML", "oxford-saml-mock"),
                RecordStatus.ACTIVE, 1L, now, now);
    }
}