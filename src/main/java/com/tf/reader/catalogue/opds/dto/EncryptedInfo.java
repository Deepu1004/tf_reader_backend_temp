package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Present only when the file is locked - absent for open access and for all audio. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncryptedInfo(String algorithm, Long originalLength) {
}
