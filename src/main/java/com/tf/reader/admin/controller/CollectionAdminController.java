package com.tf.reader.admin.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tf.reader.admin.dto.CollectionItemsResult;
import com.tf.reader.admin.dto.CollectionItemsWrite;
import com.tf.reader.admin.service.CollectionAdminService;

import jakarta.validation.Valid;


@RestController("collectionAdminController")
@RequestMapping("/api/admin/v1/collections")
public class CollectionAdminController {

	private final CollectionAdminService collections;

	public CollectionAdminController(CollectionAdminService collections) {
		this.collections = collections;
	}

	@PutMapping("/{collectionId}/items")
	public CollectionItemsResult setItems(@PathVariable String collectionId,
			@Valid @RequestBody CollectionItemsWrite body) {
		return collections.setItems(collectionId, body);
	}

}
