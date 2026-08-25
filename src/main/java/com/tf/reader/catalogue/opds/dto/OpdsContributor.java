package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The OPDS 2.0 shape for a named thing: authors, editors, narrators, the publisher and
 * subjects all use it - never a bare string. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsContributor(String name, String sortAs) {
}
