package com.tf.reader.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tf.reader.admin.controller.CatalogueItemAdminController;
import com.tf.reader.admin.dto.CatalogueItemView;
import com.tf.reader.admin.service.CatalogueItemAdminService;
import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;
import com.tf.reader.common.page.PageResponse;
import com.tf.reader.shared.error.ApiException;
import com.tf.reader.shared.error.ErrorCode;
import com.tf.reader.shared.error.GlobalExceptionHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * HTTP surface of the four catalogue item admin endpoints: status codes, serialised shape, and the
 * error envelope. The service is mocked; business rules are tested in
 * {@link CatalogueItemAdminServiceTest}. Security filters are excluded so shape/status assertions
 * don't need a JWT.
 */
@WebMvcTest(controllers = CatalogueItemAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CatalogueItemAdminControllerTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	ObjectMapper json;

	@MockitoBean
	CatalogueItemAdminService service;

	private static final Instant CREATED = Instant.parse("2026-08-10T09:00:00Z");

	private static CatalogueItemView summaryView() {
		return new CatalogueItemView("item_42", "pub_rtlg", null, List.of("col_law2024"), "Rights for Robots", null,
				List.of("Joshua C. Gellers"), List.of(), List.of(), "9780367211745", ContentType.PDF,
				AccessTier.ELITE, List.of("Law"), "en", null, null, null, null, null, ItemStatus.PUBLISHED,
				ContentState.QUEUED, null, List.of(), CREATED, CREATED);
	}

	private static CatalogueItemView fullView() {
		CatalogueItemView.Asset asset = new CatalogueItemView.Asset(ContentType.PDF, "application/pdf", 1024L, null,
				true, true, null, 500);
		return new CatalogueItemView("item_42", "pub_rtlg", "Routledge", List.of("col_law2024"),
				"Rights for Robots", null, List.of("Joshua C. Gellers"), List.of(), List.of(), "9780367211745",
				ContentType.PDF, AccessTier.ELITE, List.of("Law"), "en", null, null, null, null, null,
				ItemStatus.PUBLISHED, ContentState.QUEUED, null, List.of(asset), CREATED, CREATED);
	}

	// ---------------------------------------------------------------- list

	@Test
	@SuppressWarnings("unchecked")
	void listReturns200WithSummaryShapeAndNoAssets() throws Exception {
		when(service.list(any(), any(), any(), any(), any(), any(), any(), any()))
				.thenReturn(new PageResponse<>(List.of(summaryView()), 0, 20, 1));

		String body = mvc.perform(get("/api/admin/v1/catalogue-items")).andExpect(status().isOk())
				.andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.total").value(1)).andReturn()
				.getResponse().getContentAsString();

		Map<String, Object> parsed = json.readValue(body, Map.class);
		Map<String, Object> item = ((List<Map<String, Object>>) parsed.get("items")).get(0);
		assertThat(item.get("assets")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();
		assertThat(item.get("publisherName")).isNull();
	}

	// ---------------------------------------------------------------- create

	@Test
	void createReturns201() throws Exception {
		when(service.create(any())).thenReturn(fullView());

		mvc.perform(post("/api/admin/v1/catalogue-items").contentType(MediaType.APPLICATION_JSON).content("""
				{"publisherId":"pub_rtlg","title":"Rights for Robots","contentType":"PDF","accessTier":"ELITE"}
				""")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("item_42"));
	}

	@Test
	void createWithMissingRequiredFieldIs400() throws Exception {
		mvc.perform(post("/api/admin/v1/catalogue-items").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"No publisher or contentType\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void createAgainstAnUnknownPublisherIs400NotA500() throws Exception {
		when(service.create(any()))
				.thenThrow(new ApiException(ErrorCode.VALIDATION_FAILED, "publisherId does not reference an existing publisher"));

		mvc.perform(post("/api/admin/v1/catalogue-items").contentType(MediaType.APPLICATION_JSON).content("""
				{"publisherId":"does-not-exist","title":"x","contentType":"PDF","accessTier":"ELITE"}
				""")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	// ---------------------------------------------------------------- get

	@Test
	void getReturns200WithFullDetail() throws Exception {
		when(service.get("item_42")).thenReturn(fullView());

		mvc.perform(get("/api/admin/v1/catalogue-items/item_42")).andExpect(status().isOk())
				.andExpect(jsonPath("$.publisherName").value("Routledge"))
				.andExpect(jsonPath("$.assets.length()").value(1));
	}

	@Test
	void getUnknownItemIs404() throws Exception {
		when(service.get("item_nope")).thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such catalogue item"));

		mvc.perform(get("/api/admin/v1/catalogue-items/item_nope")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	// ---------------------------------------------------------------- update

	@Test
	void updateReturns200() throws Exception {
		when(service.update(eq("item_42"), any())).thenReturn(fullView());

		mvc.perform(put("/api/admin/v1/catalogue-items/item_42").contentType(MediaType.APPLICATION_JSON).content("""
				{"publisherId":"pub_rtlg","title":"Rights for Robots, 2e","contentType":"PDF","accessTier":"ELITE"}
				""")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("item_42"));
	}

}
