package com.tf.reader.catalogue.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "catalogueItems")
@CompoundIndexes({
		@CompoundIndex(name = "publisher_status", def = "{'publisherId': 1, 'status': 1}"),
		@CompoundIndex(name = "collections_status_contentstate", def = "{'collectionIds': 1, 'status': 1, 'contentState': 1}"),
		@CompoundIndex(name = "accesstier_status", def = "{'accessTier': 1, 'status': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogueItem {

	@Id
	private String id;

	private String publisherId;
	private List<String> collectionIds;

	@TextIndexed
	private String title;

	private String subtitle;

	@TextIndexed
	private List<String> authors;

	private List<String> editors;
	private List<String> narrators;

	// Unique so two admins posting the same book at the same moment cannot both pass
	// CatalogueItemAdminService's check-then-act duplicate test. Sparse because most items have no
	// ISBN at all - audio and ingest-first drafts - and a sparse index leaves documents missing the
	// field out entirely, so any number of them coexist. Mongo's duplicate key error already maps
	// to CODE_TAKEN in GlobalExceptionHandler, so the race loser sees the ordinary 409.
	@Indexed(sparse = true, unique = true)
	private String isbn;

	private String language;

	@TextIndexed
	private String description;

	@TextIndexed
	private List<String> subjects;

	private LocalDate publishedAt;


	private Integer numberOfPages;

	private Integer duration;

	// Set only when the cover was uploaded through the admin console, not pasted in as an
	// external link. The bucket is private, so coverUrl is never derived from these at write
	// time - CoverUrlResolver presigns fresh from coverKey on every read instead.
	private String coverUrl;
	private String coverKey;
	private String coverMimeType;
	private ContentType contentType;
	private AccessTier accessTier;
	private ItemStatus status;
	private ContentState contentState;
	private String contentError;
	private List<Asset> assets;

	// Never leave the server; excluded from any future DTO.
	private String storageKey;
	private String indexKey;
	private String masterWrappedBek;

	private Instant createdAt;
	private Instant updatedAt;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Asset {

		private ContentType format;
		private String mimeType;
		private long sizeBytes;
		private long cipherLength;
		private boolean encrypted;
		private boolean hasSearchIndex;
		private int indexTerms;
		private String indexSkipReason;
		private String keyId;

	}

}
