package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** One curated shelf inside the root feed: a title, a link to the full shelf, and the
 * first few books inline. Groups do not nest. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsGroup(
        OpdsGroupMetadata metadata,
        List<OpdsLink> links,
        List<OpdsPublication> publications) {
}
