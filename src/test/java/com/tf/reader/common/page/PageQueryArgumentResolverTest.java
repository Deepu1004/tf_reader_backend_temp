package com.tf.reader.common.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.tf.reader.common.error.ApiException;

class PageQueryArgumentResolverTest {

	private final PageQueryArgumentResolver resolver = new PageQueryArgumentResolver();

	@Test
	void defaultsToPageZeroAndSizeTwentyWhenNoParamsGiven() {
		PageQuery query = (PageQuery) resolver.resolveArgument(null, null,
				new ServletWebRequest(new MockHttpServletRequest()), null);

		assertThat(query.page()).isEqualTo(0);
		assertThat(query.size()).isEqualTo(20);
	}

	@Test
	void readsPageAndSizeFromQueryParams() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("page", "3");
		request.setParameter("size", "50");

		PageQuery query = (PageQuery) resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

		assertThat(query.page()).isEqualTo(3);
		assertThat(query.size()).isEqualTo(50);
	}

	@Test
	void rejectsANegativePage() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("page", "-1");

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("page");
	}

	@Test
	void rejectsASizeAboveOneHundred() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("size", "101");

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("size must be between 1 and 100");
	}

	@Test
	void rejectsASizeOfZero() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("size", "0");

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void rejectsANonNumericValue() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setParameter("page", "abc");

		assertThatThrownBy(() -> resolver.resolveArgument(null, null, new ServletWebRequest(request), null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("page must be a number");
	}

}
