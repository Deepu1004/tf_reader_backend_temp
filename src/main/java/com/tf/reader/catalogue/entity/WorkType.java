package com.tf.reader.catalogue.entity;

/**
 * What kind of catalogue item this is. {@code BOOK} and {@code ARTICLE} are leaves - they carry
 * content and go through ingest. {@code JOURNAL}/{@code VOLUME}/{@code ISSUE} are pure
 * containers: no content, no {@code contentState} transitions, holding other items as children
 * via {@code parentId}.
 */
public enum WorkType {
	BOOK,
	JOURNAL,
	VOLUME,
	ISSUE,
	ARTICLE
}
