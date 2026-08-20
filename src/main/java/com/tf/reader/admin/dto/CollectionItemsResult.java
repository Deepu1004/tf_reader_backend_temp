package com.tf.reader.admin.dto;

import java.util.List;


public record CollectionItemsResult(String collectionId, long itemCount, List<String> affectedInstitutions) {}
