package com.tf.reader.catalogue.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * The BatchItemsRequest schema. {@code ids} must be non-empty, checked here with bean
 * validation. The upper bound of 100 is not: it gets its own {@code TOO_MANY_IDS} code, which
 * {@code CatalogueBatchService} throws before any database call, ahead of the general
 * {@code VALIDATION_FAILED} a {@code @Size} annotation would produce.
 */
public record BatchItemsRequest(@NotEmpty List<String> ids) {
}
