package com.tf.reader.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tf.reader.admin.controller.TenantAdminController;
import com.tf.reader.admin.dto.DatabaseWrite;
import com.tf.reader.admin.dto.TenantView;
import com.tf.reader.admin.service.TenantAdminService;
import com.tf.reader.catalogue.entity.VaultConnectionHealth;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.common.error.GlobalExceptionHandler;

/**
 * HTTP surface of the two tenant admin endpoints. The service is mocked; the SUPER_ADMIN check and
 * not-found handling are tested in {@link TenantAdminServiceTest}.
 */
@WebMvcTest(controllers = TenantAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TenantAdminControllerTest {

	@Autowired MockMvc mvc;

	@MockitoBean TenantAdminService service;

	@Test
	@DisplayName("list returns 200 with every tenant's vault fields")
	void listReturns200() throws Exception {
		when(service.list()).thenReturn(
				List.of(new TenantView("pub_r1", "ROUTLEDGE", "Routledge", null, VaultConnectionHealth.NOT_CONFIGURED)));

		mvc.perform(get("/api/admin/v1/tenants")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value("pub_r1"))
				.andExpect(jsonPath("$[0].connectionHealth").value("NOT_CONFIGURED"));
	}

	@Test
	@DisplayName("GET unknown tenant returns 404 NOT_FOUND in the envelope")
	void unknownTenantIs404() throws Exception {
		when(service.get(any())).thenThrow(new ApiException(ErrorCode.NOT_FOUND, "No such publisher"));

		mvc.perform(get("/api/admin/v1/tenants/pub_nope")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND")).andExpect(jsonPath("$.traceId").exists());
	}

	@Test
	@DisplayName("PUT database returns 200 with the updated connection health")
	void setDatabaseReturns200() throws Exception {
		when(service.setDatabase(org.mockito.ArgumentMatchers.eq("pub_r1"), any())).thenReturn(
				new TenantView("pub_r1", "ROUTLEDGE", "Routledge", null, VaultConnectionHealth.HEALTHY));

		mvc.perform(put("/api/admin/v1/tenants/pub_r1/database").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mongoUri\":\"mongodb://localhost:27018/pub01\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.connectionHealth").value("HEALTHY"));
	}

	@Test
	@DisplayName("PUT database for another publisher returns 403 FORBIDDEN_ROLE in the envelope")
	void setDatabaseForAnotherPublisherIs403() throws Exception {
		when(service.setDatabase(org.mockito.ArgumentMatchers.eq("pub_other"), any()))
				.thenThrow(new ApiException(ErrorCode.FORBIDDEN_ROLE, "Not permitted to access this publisher"));

		mvc.perform(put("/api/admin/v1/tenants/pub_other/database").contentType(MediaType.APPLICATION_JSON)
				.content("{\"mongoUri\":\"mongodb://localhost:27019/pub02\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
	}

	@Test
	@DisplayName("PUT vault-key returns 200 with the updated vaultRef")
	void setVaultKeyReturns200() throws Exception {
		when(service.setVaultKey(org.mockito.ArgumentMatchers.eq("pub_r1"), any())).thenReturn(
				new TenantView("pub_r1", "ROUTLEDGE", "Routledge", "pub_r1", VaultConnectionHealth.NOT_CONFIGURED));

		mvc.perform(put("/api/admin/v1/tenants/pub_r1/vault-key").contentType(MediaType.APPLICATION_JSON)
				.content("{\"keyBase64\":\"LjUpFKAqZVHITGhhW6Q/DwIciLJN+gpYkh+VLnirvj0=\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.vaultRef").value("pub_r1"));
	}

}
