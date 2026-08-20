package com.tf.reader.admin;

import tools.jackson.databind.ObjectMapper;
import com.tf.reader.admin.controller.PublisherAdminController;
import com.tf.reader.admin.dto.PublisherView;
import com.tf.reader.admin.dto.PublisherWrite;
import com.tf.reader.admin.dto.StatusChange;
import com.tf.reader.admin.service.PublisherAdminService;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.GlobalExceptionHandler;
import com.tf.reader.common.model.RecordStatus;
import com.tf.reader.common.page.PageResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP surface of the five publisher admin endpoints: status codes, serialised
 * shape, and the error envelope. The service is mocked; business rules are
 * tested in {@link PublisherAdminServiceTest}.
 *
 * <p>
 * Security filters are excluded ({@code addFilters = false}) so shape and
 * status-code assertions are fast and don't need a JWT. The scope guard on the
 * list endpoint ({@code @adminScope.isSuperAdmin()}) is verified in
 * {@link PublisherAdminListSecurityTest}.
 */
@WebMvcTest(controllers = PublisherAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PublisherAdminControllerTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ObjectMapper json;

	@MockitoBean
	PublisherAdminService service;

	private static final Instant CREATED = Instant.parse("2026-08-01T10:00:00Z");

	private static PublisherView routledgeView() {
		return new PublisherView("pub_r1", "ROUTLEDGE", "Routledge", "Academic imprint of Taylor and Francis",
				"https://cdn.tf/logos/routledge.png", RecordStatus.ACTIVE, 42, 3, CREATED);
	}

	// ---------------------------------------------------------------- list

	@Test
	@DisplayName("list returns 200 with the four page keys and correct item shape")
	@SuppressWarnings("unchecked")
	void listReturns200WithPageShape() throws Exception {
		when(service.list(any(), any(), any()))
				.thenReturn(new PageResponse<>(List.of(routledgeView()), 0, 20, 1));

		String body = mvc.perform(get("/api/admin/v1/publishers")).andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.total").value(1)).andReturn().getResponse().getContentAsString();

		Map<String, Object> parsed = json.readValue(body, Map.class);
		assertThat(parsed.keySet()).containsExactlyInAnyOrder("items", "page", "size", "total");

		Map<String, Object> item = ((List<Map<String, Object>>) parsed.get("items")).get(0);
		assertThat(item.keySet()).containsExactlyInAnyOrder("id", "code", "name", "description", "logoUrl", "status",
				"itemCount", "collectionCount", "createdAt");
	}

	// ---------------------------------------------------------------- create

	@Test
	@DisplayName("POST create returns 201 with the publisher body")
	void createReturns201() throws Exception {
		when(service.create(any())).thenReturn(routledgeView());

		mvc.perform(post("/api/admin/v1/publishers").contentType(MediaType.APPLICATION_JSON).content("""
				{"code":"routledge","name":"Routledge",
				 "description":"Academic imprint","logoUrl":"https://cdn.tf/logos/routledge.png"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("pub_r1"))
				.andExpect(jsonPath("$.code").value("ROUTLEDGE")).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	@DisplayName("POST with a duplicate code returns 409 CODE_TAKEN in the envelope")
	void duplicateCodeIs409() throws Exception {
		when(service.create(any()))
				.thenThrow(new ApiException(ErrorCode.CODE_TAKEN, "Publisher code 'routledge' is already taken"));

		mvc.perform(post("/api/admin/v1/publishers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"routledge\",\"name\":\"Routledge\"}")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CODE_TAKEN")).andExpect(jsonPath("$.traceId").exists());
	}

	@Test
	@DisplayName("POST with an invalid code pattern returns 400 VALIDATION_FAILED")
	void invalidCodePatternIs400() throws Exception {
		mvc.perform(post("/api/admin/v1/publishers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"UPPER_CASE\",\"name\":\"Bad\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	// ---------------------------------------------------------------- get

	@Test
	@DisplayName("GET by id returns 200 with the publisher")
	void getReturns200() throws Exception {
		when(service.get("pub_r1")).thenReturn(routledgeView());

		mvc.perform(get("/api/admin/v1/publishers/pub_r1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("pub_r1")).andExpect(jsonPath("$.itemCount").value(42))
				.andExpect(jsonPath("$.collectionCount").value(3));
	}

	@Test
	@DisplayName("GET unknown publisher returns 404 NOT_FOUND in the envelope")
	void unknownPublisherIs404() throws Exception {
		when(service.get(any())).thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));

		mvc.perform(get("/api/admin/v1/publishers/pub_nope")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.traceId").exists());
	}

	// ---------------------------------------------------------------- update

	@Test
	@DisplayName("PUT update returns 200 with the updated publisher")
	void updateReturns200() throws Exception {
		when(service.update(eq("pub_r1"), any())).thenReturn(routledgeView());

		mvc.perform(put("/api/admin/v1/publishers/pub_r1").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"routledge\",\"name\":\"Routledge Updated\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("pub_r1"));
	}

	// ---------------------------------------------------------------- status

	@Test
	@DisplayName("PATCH status returns 200 with the updated publisher")
	void changeStatusReturns200() throws Exception {
		PublisherView suspended = new PublisherView("pub_r1", "ROUTLEDGE", "Routledge", null, null,
				RecordStatus.SUSPENDED, 42, 3, CREATED);
		when(service.changeStatus(eq("pub_r1"), any())).thenReturn(suspended);

		mvc.perform(patch("/api/admin/v1/publishers/pub_r1/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"SUSPENDED\",\"reason\":\"contract under review\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUSPENDED"));
	}

	@Test
	@DisplayName("PATCH status with missing status field returns 400")
	void missingStatusFieldIs400() throws Exception {
		mvc.perform(patch("/api/admin/v1/publishers/pub_r1/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"no status supplied\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
