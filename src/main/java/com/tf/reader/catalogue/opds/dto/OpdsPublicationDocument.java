package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsPublicationDocument(
        @JsonProperty("@context") String context,
        OpdsPublicationMetadata metadata,
        List<OpdsLink> links,
        List<OpdsImageLink> images) {

    private static final String WEBPUB_CONTEXT = "https://readium.org/webpub-manifest/context.jsonld";

    public OpdsPublicationDocument(OpdsPublication publication) {
        this(WEBPUB_CONTEXT, publication.metadata(), publication.links(), publication.images());
    }

    // Same shared shape, built from the institution-side publication detail (OpdsFeedService)
    // rather than the public one - the context is fixed either way, never per-caller.
    public OpdsPublicationDocument(OpdsPublicationMetadata metadata, List<OpdsLink> links,
            List<OpdsImageLink> images) {
        this(WEBPUB_CONTEXT, metadata, links, images);
    }
}
