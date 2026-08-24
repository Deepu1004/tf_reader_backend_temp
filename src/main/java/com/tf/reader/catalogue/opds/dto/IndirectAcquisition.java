package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** What you eventually get, as opposed to what the acquisition href itself returns (which
 * is flambeau JSON, not the book). Standard OPDS 2.0 field for a link that leads to a file
 * rather than being one. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndirectAcquisition(String type) {
}
