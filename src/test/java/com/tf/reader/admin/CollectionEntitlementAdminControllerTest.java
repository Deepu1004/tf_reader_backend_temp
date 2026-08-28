package com.tf.reader.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tf.reader.admin.controller.CollectionEntitlementAdminController;
import com.tf.reader.admin.dto.CollectionEntitlementView;
import com.tf.reader.admin.security.AdminScopeAuthorizer;
import com.tf.reader.admin.service.CollectionEntitlementAdminService;
import com.tf.reader.common.error.GlobalExceptionHandler;
import com.tf.reader.common.page.PageResponse;

/**
 * HTTP surface of GET /api/admin/v1/collections: status codes and the serialised shape. Business
 * rules are tested in {@link CollectionEntitlementAdminServiceTest}. Security filters are
 * excluded so shape/status assertions don't need a JWT.
 */
@WebMvcTest(controllers = CollectionEntitlementAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ GlobalExceptionHandler.class, AdminScopeAuthorizer.class })
class CollectionEntitlementAdminControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	CollectionEntitlementAdminService service;

	@Test
	void listReturns200WithEntitlementStatusPerCollection() throws Exception {
		var view = new CollectionEntitlementView("col_law2024", "pub_rtlg", "LAW2024", "Law Essentials", null,
				"active");
		when(service.list(any(), any(), any(), any(), any())).thenReturn(new PageResponse<>(List.of(view), 0, 20, 1));

		mvc.perform(get("/api/admin/v1/collections")).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].entitlementStatus").value("active"));
	}

}
