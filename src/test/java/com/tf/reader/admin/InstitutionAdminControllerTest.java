package com.tf.reader.admin;

import com.tf.reader.admin.controller.InstitutionAdminController;
import com.tf.reader.admin.dto.AdminInstitution;
import com.tf.reader.admin.dto.InstitutionSummary;
import com.tf.reader.admin.dto.InstitutionWrite;
import com.tf.reader.admin.dto.SignInWrite;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.InstitutionAdminService;
import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.dto.SignInView;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.GlobalExceptionHandler;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.common.security.TokenClaims;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks status codes, response shapes and the error envelope, with security switched off so a
 * request reaches the controller directly. Who is allowed to call which endpoint is checked
 * separately, against the real security setup.
 */
@WebMvcTest(controllers = InstitutionAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ GlobalExceptionHandler.class, AdminScopeAuthorizer.class })
class InstitutionAdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockitoBean InstitutionAdminService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Puts a JWT with the given role (and, optionally, an institutionId claim) into the security
     * context — the controller reads these claims directly to work out the list's scope. */
    private static void actingAs(String role, String institutionId) {
        Jwt.Builder tokenBuilder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("u_test")
                .claim(TokenClaims.ROLE, role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        if (institutionId != null) {
            tokenBuilder.claim(TokenClaims.SCOPE_INSTITUTION_ID, institutionId);
            }
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(tokenBuilder.build(), null, List.of()));
    }

    private static AdminInstitution oxford() {
        return new AdminInstitution(
                "inst_ox", "oxford", "University of Oxford", "UK", "Oxford",
                new BrandingView("https://cdn.tf/logos/oxford.png", "#002147"),
                new SignInView("SAML", "oxford-saml-mock"),
                "http://localhost:8080/opds/v1/institutions/inst_ox/catalogue",
                InstitutionType.ACADEMIC, List.of("ox.ac.uk"), RecordStatus.ACTIVE, 1L,
                new InstitutionSummary(0, 0, "http://localhost:8080/opds/v1/institutions/inst_ox/catalogue"));
    }

    @Test
    @DisplayName("the list body has exactly the four page keys, and each item has exactly 13")
    @SuppressWarnings("unchecked")
    void listBodyIsTheExpectedShape() throws Exception {
        actingAs("SUPER_ADMIN", null);
        when(service.list(any(), any(), any(), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(oxford()), 0, 20, 1));

        String body = mvc.perform(get("/api/admin/v1/institutions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> parsed = json.readValue(body, Map.class);
        assertThat(parsed.keySet()).containsExactlyInAnyOrder("items", "page", "size", "total");

        Map<String, Object> item = ((List<Map<String, Object>>) parsed.get("items")).get(0);
        assertThat(item.keySet()).containsExactlyInAnyOrder(
                "id", "code", "name", "country", "city", "branding", "signIn", "catalogueUrl",
                "type", "emailDomains", "status", "catalogueVersion", "summary");
    }

    @Test
    @DisplayName("the list endpoint reads the caller's own institution claim and passes it straight through")
    void listPassesTheCallersScopeToTheService() throws Exception {
        actingAs("INSTITUTION_ADMIN", "inst_ox");
        when(service.list(any(), any(), eq("inst_ox"), eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(oxford()), 0, 20, 1));

        mvc.perform(get("/api/admin/v1/institutions")).andExpect(status().isOk());

        verify(service).list(any(), any(), eq("inst_ox"), eq(0), eq(20));
    }

    @Test
    @DisplayName("create returns 201")
    void createReturns201() throws Exception {
        when(service.create(any())).thenReturn(oxford());

        InstitutionWrite request = new InstitutionWrite(
                "oxford", "University of Oxford", InstitutionType.ACADEMIC, "UK", "Oxford",
                List.of("ox.ac.uk"), new SignInWrite("SAML", "oxford-saml-mock"), null);

        mvc.perform(post("/api/admin/v1/institutions")
                        .contentType("application/json")
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("oxford"));
    }

    @Test
    @DisplayName("a duplicate code on create is 409 in the shared envelope")
    void duplicateCodeIs409() throws Exception {
        when(service.create(any())).thenThrow(new ApiException(ErrorCode.CODE_TAKEN, "That code is already used"));

        mvc.perform(post("/api/admin/v1/institutions")
                        .contentType("application/json")
                        .content("{\"code\":\"oxford\",\"name\":\"x\",\"type\":\"ACADEMIC\",\"country\":\"UK\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_TAKEN"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("an unknown institution is 404 in the shared envelope")
    void unknownInstitutionIs404() throws Exception {
        when(service.get("inst_ghost")).thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such institution"));

        mvc.perform(get("/api/admin/v1/institutions/inst_ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("status change returns the updated institution")
    void statusChangeReturnsUpdatedInstitution() throws Exception {
        AdminInstitution suspended = withStatus(oxford(), RecordStatus.SUSPENDED);
        when(service.setStatus("inst_ox", RecordStatus.SUSPENDED, "budget review")).thenReturn(suspended);

        mvc.perform(patch("/api/admin/v1/institutions/inst_ox/status")
                        .contentType("application/json")
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"budget review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("page=-1 is a 400 in the shared envelope")
    void negativePageIs400() throws Exception {
        mvc.perform(get("/api/admin/v1/institutions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private static AdminInstitution withStatus(AdminInstitution i, RecordStatus status) {
        return new AdminInstitution(i.id(), i.code(), i.name(), i.country(), i.city(), i.branding(),
                i.signIn(), i.catalogueUrl(), i.type(), i.emailDomains(), status, i.catalogueVersion(),
                i.summary());
    }
}