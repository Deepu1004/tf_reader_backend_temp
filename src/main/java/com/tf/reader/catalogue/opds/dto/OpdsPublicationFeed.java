package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The list shape: one shelf, or the whole entitled catalogue. Exactly one of
 * {@code publications} or {@code navigation} is populated - {@code navigation} carries a
 * single link back to the catalogue for the empty-result case, since the OPDS schema
 * forbids an empty {@code publications} array. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsPublicationFeed(
        OpdsFeedMetadata metadata,
        List<OpdsLink> links,
        List<OpdsPublication> publications,
        List<OpdsLink> navigation) {
}
