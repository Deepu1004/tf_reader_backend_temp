package com.tf.reader.common.page;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;


public class PageQueryArgumentResolver implements HandlerMethodArgumentResolver {

	private static final int DEFAULT_PAGE = 0;
	private static final int DEFAULT_SIZE = 20;
	private static final int MAX_SIZE = 100;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.getParameterType().equals(PageQuery.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		int page = intParam(webRequest, "page", DEFAULT_PAGE);
		int size = intParam(webRequest, "size", DEFAULT_SIZE);

		if (page < 0) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must not be negative");
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and " + MAX_SIZE);
		}

		return new PageQuery(page, size);
	}

	private int intParam(NativeWebRequest webRequest, String name, int defaultValue) {
		String value = webRequest.getParameter(name);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, name + " must be a number");
		}
	}

}
