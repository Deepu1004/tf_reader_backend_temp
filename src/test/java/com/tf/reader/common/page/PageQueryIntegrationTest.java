package com.tf.reader.common.page;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PageQueryTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PageQueryIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void resolvesDefaultsWhenNoQueryParamsAreGiven() throws Exception {
		mockMvc.perform(get("/test/page-query"))
				.andExpect(status().isOk())
				.andExpect(content().string("0:20"));
	}

	@Test
	void resolvesTheGivenPageAndSize() throws Exception {
		mockMvc.perform(get("/test/page-query").param("page", "2").param("size", "50"))
				.andExpect(status().isOk())
				.andExpect(content().string("2:50"));
	}

	@Test
	void anOutOfRangeSizeReachesGlobalExceptionHandlerAsTheStandardErrorResponse() throws Exception {
		mockMvc.perform(get("/test/page-query").param("size", "500"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.message").value("size must be between 1 and 100"))
				.andExpect(jsonPath("$.path").value("/test/page-query"))
				.andExpect(jsonPath("$.traceId").exists());
	}

}
