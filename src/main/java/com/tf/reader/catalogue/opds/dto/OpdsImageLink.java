package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A cover image. {@code width}/{@code height} are emitted only when known - covers are
 * URLs an operator pastes in, pointing at a bucket we never read, so dimensions are omitted
 * rather than guessed. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsImageLink(String href, String type, Integer width, Integer height) {
}
