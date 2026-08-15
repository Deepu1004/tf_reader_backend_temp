package com.tf.reader.catalogue.dto;

/**
 * One row of the find-your-institution screen. Six keys, exactly as published in
 * API-Contract-Institutions.
 *
 * <p>No {@code type}: it is internal and team1 does not render it. Nothing here is more than a
 * university already puts on its own website, which is what makes this endpoint safe to leave open.
 *
 * <p>{@code branding} is nested rather than flattened so the client has one model of an institution
 * instead of two that disagree.
 */
public record InstitutionListItem(
        String id,
        String code,
        String name,
        String country,
        String city,
        BrandingView branding) {}