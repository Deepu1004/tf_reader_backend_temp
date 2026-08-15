package com.tf.reader.catalogue.dto;

/**
 * Wire shape of an institution's branding.
 *
 * <p>Deliberately not the entity's {@code Branding}, even though the fields match today. This one is
 * frozen with team1; Person B should stay free to rename inside their document without breaking a
 * client. The {@code View} suffix just lets the mapper import both without qualifying either.
 */
public record BrandingView(String logoUrl, String primaryColor) {}