package com.tf.reader.catalogue;

// Jackson 3: Spring Boot 4 auto-configures tools.jackson, not com.fasterxml.
import tools.jackson.databind.ObjectMapper;
import com.tf.reader.catalogue.controller.InstitutionController;
import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.dto.InstitutionDetail;
import com.tf.reader.catalogue.dto.InstitutionListItem;
import com.tf.reader.catalogue.dto.SignInView;
import com.tf.reader.catalogue.service.InstitutionQueryService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.GlobalExceptionHandler;
import com.tf.reader.common.page.PageResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP surface: status codes, the serialised shape, and the error envelope.
 *
 * <p>The service is mocked, so nothing here re-tests the rules; this class only proves that what the
 * service returns reaches the wire in the frozen shape, and that what it throws becomes the right
 * status in the shared envelope.
 *
 * <p>The no-token case is <b>not</b> asserted here. A slice test does not load Person D's security
 * configuration, so a green result would mean nothing. It is asserted in {@code InstitutionApiIT},
 * against the real filter chain, which is the only place it can fail for the reason we care about.
 */
@WebMvcTest(controllers = InstitutionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class InstitutionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // @MockitoBean, not @MockBean. @MockBean was deprecated in Spring Boot 3.4 and REMOVED in 4.x,
    // and this repository is on Boot 4.1, so the old annotation does not compile there.
    @MockitoBean InstitutionQueryService service;

    private static InstitutionListItem imperialItem() {
        return new InstitutionListItem(
                "inst_7f3",
                "imperial",
                "Imperial College London",
                "UK",
                "London",
                new BrandingView("https://cdn.tf.example/logos/imperial.png", "#003E74"));
    }

    private static InstitutionDetail imperialDetail() {
        return new InstitutionDetail(
                "inst_7f3",
                "imperial",
                "Imperial College London",
                "UK",
                "London",
                new BrandingView("https://cdn.tf.example/logos/imperial.png", "#003E74"),
                new SignInView("SAML", "imperial-saml-mock"),
                "http://localhost:8080/opds/v1/institutions/inst_7f3/catalogue");
    }

    @Test
    @DisplayName("the list body has exactly the four page keys and exactly six keys per item")
    @SuppressWarnings("unchecked")
    void listBodyIsTheFrozenShape() throws Exception {
        when(service.list(any())).thenReturn(new PageResponse<>(List.of(imperialItem()), 0, 20, 3));

        String body =
                mvc.perform(get("/api/v1/institutions"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(20))
                        .andExpect(jsonPath("$.total").value(3))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Map<String, Object> parsed = json.readValue(body, Map.class);
        assertThat(parsed.keySet()).containsExactlyInAnyOrder("items", "page", "size", "total");

        // The leak test. Exactly six keys, no more: adding one later is safe, but a stray field that
        // reached team1 once cannot be withdrawn, and "type" reaching a client would be a real leak.
        Map<String, Object> item = ((List<Map<String, Object>>) parsed.get("items")).get(0);
        assertThat(item.keySet())
                .containsExactlyInAnyOrder("id", "code", "name", "country", "city", "branding");
        assertThat(item).doesNotContainKeys("type", "status", "catalogueVersion", "signIn");

        Map<String, Object> branding = (Map<String, Object>) item.get("branding");
        assertThat(branding.keySet()).containsExactlyInAnyOrder("logoUrl", "primaryColor");
    }

    @Test
    @DisplayName("the detail body has exactly eight keys")
    @SuppressWarnings("unchecked")
    void detailBodyIsTheFrozenShape() throws Exception {
        when(service.detail("inst_7f3")).thenReturn(imperialDetail());

        String body =
                mvc.perform(get("/api/v1/institutions/inst_7f3"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        Map<String, Object> parsed = json.readValue(body, Map.class);
        assertThat(parsed.keySet())
                .containsExactlyInAnyOrder(
                        "id", "code", "name", "country", "city", "branding", "signIn", "catalogueUrl");
        assertThat(((Map<String, Object>) parsed.get("signIn")).keySet())
                .containsExactlyInAnyOrder("method", "idpHint");
        assertThat(parsed).doesNotContainKeys("type", "status", "catalogueVersion");
    }

    @Test
    @DisplayName("an unknown institution is a 404 in the shared envelope, with a traceId")
    void unknownInstitutionIs404InTheEnvelope() throws Exception {
        when(service.detail(any()))
                .thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such institution"));

        mvc.perform(get("/api/v1/institutions/inst_nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/v1/institutions/inst_nope"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("an inactive institution is byte-identical to an unknown one")
    void inactiveAndUnknownAreIndistinguishableOnTheWire() throws Exception {
        when(service.detail(any()))
                .thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such institution"));

        String unknown = bodyOf("/api/v1/institutions/inst_does_not_exist");
        String inactive = bodyOf("/api/v1/institutions/inst_leeds");

        // path and traceId legitimately differ, so compare what a prober could learn from: the code
        // and the message.
        Map<?, ?> a = json.readValue(unknown, Map.class);
        Map<?, ?> b = json.readValue(inactive, Map.class);
        assertThat(a.get("code")).isEqualTo(b.get("code"));
        assertThat(a.get("message")).isEqualTo(b.get("message"));
        assertThat(a.keySet()).isEqualTo(b.keySet());
    }

    @Test
    @DisplayName("page=-1 is a 400 in the shared envelope, not a clamped 200")
    void negativePageIs400() throws Exception {
        when(service.list(any()))
                .thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED, "page must be zero or greater"));

        mvc.perform(get("/api/v1/institutions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("size out of range is a 400 carrying the contract's exact message")
    void sizeOutOfRangeIs400() throws Exception {
        when(service.list(any()))
                .thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and 100"));

        mvc.perform(get("/api/v1/institutions").param("size", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"))
                .andExpect(jsonPath("$.path").value("/api/v1/institutions"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("page=abc never reaches the controller and is still a 400 in the envelope")
    void nonNumericPageIs400() throws Exception {
        // Spring fails to bind before the method runs, so this is really a test of Person B's handler
        // covering MethodArgumentTypeMismatchException. Without that mapping it is a bare 400 with a
        // Spring body and no traceId, which breaks the "every failure looks the same" contract.
        mvc.perform(get("/api/v1/institutions").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").exists());
    }


    private String bodyOf(String path) throws Exception {
        return mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    }
}