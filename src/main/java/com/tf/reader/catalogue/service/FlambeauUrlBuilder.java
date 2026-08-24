package com.tf.reader.catalogue.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds acquisition hrefs into flambeau, from one configured base URL. Every OPDS
 * acquisition link points here - wokay never serves content bytes (shared.md, fact 1).
 *
 * <p>Defaults to {@code tf.catalogue.base-url} when {@code tf.flambeau.base-url} is not set:
 * this is one Spring Boot process (shared.md), so flambeau's own controllers
 * ({@code LoanController}, {@code ReadingSessionController}) already answer on the same
 * host. A separate value only needs setting if flambeau is ever split out.
 */
@Component
public class FlambeauUrlBuilder {

    private final String baseUrl;

    public FlambeauUrlBuilder(
            @Value("${tf.flambeau.base-url:${tf.catalogue.base-url}}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String loansUrlFor(String itemId) {
        return baseUrl + "/api/v1/loans?itemId=" + itemId;
    }

    public String readingSessionsUrlFor(String itemId) {
        return baseUrl + "/api/v1/reading-sessions?itemId=" + itemId;
    }
}
