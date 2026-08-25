package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Total only - we do not call flambeau while building a feed, so we cannot know how many
 * copies are free. Absent unless {@code licenceModel} is {@code ELITE}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Copies(Integer total) {
}
