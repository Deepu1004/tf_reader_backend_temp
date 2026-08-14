package com.tf.reader.auth.model;

/**
 * A business institution.
 *
 * <p>Deliberately carries no SAML configuration. Institutions are business data we own; SAML
 * is one authentication mechanism, shared by all of them. Putting an IdP URL on this record is
 * what would turn one integration into one-per-institution.
 */
public record Institution(String institutionId, String name) {
}
