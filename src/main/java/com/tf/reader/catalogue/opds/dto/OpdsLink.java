package com.tf.reader.catalogue.opds.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One flexible shape for every link in an OPDS feed: a plain feed-level link, a navigation
 * signpost, or the single acquisition link that carries {@code properties}. The contract
 * models these as three schemas ({@code OpdsLink}, {@code OpdsNavigationLink},
 * {@code OpdsPublicationLink}) built from the same fields via {@code allOf} - one record
 * here produces JSON matching all three, since the field set is identical.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsLink(
        String rel,
        String href,
        String type,
        String title,
        Boolean templated,
        OpdsLinkProperties properties) {

    public OpdsLink(String rel, String href, String type) {
        this(rel, href, type, null, null, null);
    }

    public OpdsLink(String rel, String href, String type, String title) {
        this(rel, href, type, title, null, null);
    }
}
