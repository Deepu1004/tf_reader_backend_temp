package com.tf.reader.catalogue.dto;

/**
 * One institution, after the user has picked it. Eight keys: the six from
 * {@link InstitutionListItem} plus {@code signIn} and {@code catalogueUrl}.
 *
 * <p>{@code signIn.method} is always SAML. It ships anyway so team1 writes one code path that reads
 * a value instead of a special case that assumes the answer.
 *
 * <p>{@code catalogueUrl} is built server-side. The client follows the URL we hand it rather than
 * assembling our scheme, so the scheme can change without a coordinated release.
 */
public record InstitutionDetail(
        String id,
        String code,
        String name,
        String country,
        String city,
        BrandingView branding,
        SignInView signIn,
        String catalogueUrl) {}