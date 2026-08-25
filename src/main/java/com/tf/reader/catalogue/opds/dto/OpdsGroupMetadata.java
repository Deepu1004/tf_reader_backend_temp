package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A group has a title and a count. Paging belongs to the shelf behind it, not the group. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsGroupMetadata(String title, Integer numberOfItems) {
}
