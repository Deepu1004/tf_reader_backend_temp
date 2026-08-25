package com.tf.reader.catalogue.opds.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsFeedMetadata(
        String title,
        Integer numberOfItems,
        Integer itemsPerPage,
        Integer currentPage,
        Instant modified) {
}
