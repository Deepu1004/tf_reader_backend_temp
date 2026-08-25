package com.tf.reader.catalogue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Builds the address of an institution's catalogue feed, from one configured base URL. */
@Component
public class CatalogueUrlBuilder {

    private final String baseUrl;

    public CatalogueUrlBuilder(@Value("${tf.catalogue.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String catalogueUrlFor(String institutionId) {
        return baseUrl + "/opds/v1/institutions/" + institutionId + "/catalogue";
    }

    public String groupUrlFor(String institutionId, String groupId) {
        return baseUrl + "/opds/v1/institutions/" + institutionId + "/groups/" + groupId;
    }

    public String publicationUrlFor(String institutionId, String itemId) {
        return baseUrl + "/opds/v1/institutions/" + institutionId + "/publications/" + itemId;
    }

    /** RFC 6570 template - {@code {?query}} is filled in by the OPDS client, not by us. */
    public String searchUrlTemplateFor(String institutionId) {
        return baseUrl + "/opds/v1/institutions/" + institutionId + "/search{?query}";
    }
}
