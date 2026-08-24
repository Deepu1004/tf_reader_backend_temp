package com.tf.reader.catalogue.opds.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsPublicationMetadata(
        @JsonProperty("@type") String type,
        String identifier,
        String title,
        String subtitle,
        List<OpdsContributor> author,
        List<OpdsContributor> editor,
        List<OpdsContributor> narrator,
        OpdsContributor publisher,
        String language,
        LocalDate published,
        Instant modified,
        String description,
        Integer numberOfPages,
        Integer duration,
        List<OpdsContributor> subject) {
}
