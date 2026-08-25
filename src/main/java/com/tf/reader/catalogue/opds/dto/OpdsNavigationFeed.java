package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The root feed shape: signposts and shelves, no top-level publications. {@code groups}
 * is omitted entirely (not an empty array) when no curated shelf survives filtering. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsNavigationFeed(
        OpdsFeedMetadata metadata,
        List<OpdsLink> links,
        List<OpdsLink> navigation,
        List<OpdsGroup> groups) {
}
