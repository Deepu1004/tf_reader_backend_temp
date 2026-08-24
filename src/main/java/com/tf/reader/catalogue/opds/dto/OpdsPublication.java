package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsPublication(
        OpdsPublicationMetadata metadata,
        List<OpdsLink> links,
        List<OpdsImageLink> images) {
}
