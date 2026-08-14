package com.tf.reader.common.page;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageQueryTestController {

	@GetMapping("/test/page-query")
	public String pageQuery(PageQuery query) {
		return query.page() + ":" + query.size();
	}

}
