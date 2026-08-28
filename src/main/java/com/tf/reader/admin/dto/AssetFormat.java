package com.tf.reader.admin.dto;

/**
 * What an uploaded file is, per the ingest contract. Same three values as
 * {@link com.tf.reader.catalogue.entity.ContentType} today - a separate name because the two
 * answer different questions ("what was just uploaded" vs "what is this book"), the same
 * reasoning the OpenAPI spec gives for keeping them distinct schemas. Mirrors the precedent of
 * {@code content.api.Format}, a third identically-shaped enum for the same three values.
 */
public enum AssetFormat {
	PDF,
	EPUB,
	AUDIO
}
