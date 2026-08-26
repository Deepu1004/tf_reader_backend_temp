package com.tf.reader.catalogue.opds.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tf.reader.catalogue.entity.AccessTier;

/**
 * Extra facts about an acquisition link - nothing else in the feed has these. Reuses the
 * existing {@link AccessTier} enum for {@code licenceModel}: same three values as the book's
 * own {@code accessTier}, so there is nothing to translate.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpdsLinkProperties(
        AccessTier licenceModel,
        List<IndirectAcquisition> indirectAcquisition,
        Copies copies,
        EncryptedInfo encrypted,
        Boolean hasSearchIndex,
        Boolean canPersist,
        Long fileSize,
        OpdsAvailability availability) {

    // A subscribe link on a public discovery route: no file to describe, just where to get one.
    public OpdsLinkProperties(AccessTier licenceModel, OpdsAvailability availability) {
        this(licenceModel, null, null, null, null, null, null, availability);
    }
}
