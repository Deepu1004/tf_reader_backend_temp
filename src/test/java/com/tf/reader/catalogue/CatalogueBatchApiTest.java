package com.tf.reader.catalogue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.tf.reader.TestcontainersConfiguration;

/**
 * {@code POST /api/v1/catalogue/items:batch} through the real security filter chain.
 *
 * <p>Only the token boundary is proven here; every business rule about which ids come back in
 * which list is {@link com.tf.reader.catalogue.service.CatalogueBatchServiceTest}, against a
 * mocked repository and a mocked {@code EntitlementQuery}, not a real one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogueBatchApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void noBearerTokenAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/catalogue/items:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"ids\":[\"item_1\"]}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

}
