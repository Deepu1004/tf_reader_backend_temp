package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The standalone shape {@code application/opds-publication+json} returns for one book:
 * the same fields as {@link OpdsPublication}, plus {@code @context}, with no {@code publications}
 * array to nest inside - {@code metadata}, {@code links} and {@code images} sit at the top level. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsPublicationDocument(
        @JsonProperty("@context") String context,
        OpdsPublicationMetadata metadata,
        List<OpdsLink> links,
        List<OpdsImageLink> images) {

    private static final String WEBPUB_CONTEXT = "https://readium.org/webpub-manifest/context.jsonld";

    public OpdsPublicationDocument(OpdsPublicationMetadata metadata, List<OpdsLink> links, List<OpdsImageLink> images) {
        this(WEBPUB_CONTEXT, metadata, links, images);
    }
}
