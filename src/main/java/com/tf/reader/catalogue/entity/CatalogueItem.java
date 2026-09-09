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
		@CompoundIndex(name = "accesstier_status", def = "{'accessTier': 1, 'status': 1}"),
		// Backs a container's children lookup (Journal -> its Volumes, Volume -> its Issues, ...).
		@CompoundIndex(name = "parent_worktype", def = "{'parentId': 1, 'workType': 1}")
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

	// Defaults to BOOK when absent, so every item that existed before this field was added is a
	// standalone book, unaffected without a migration. Only BOOK and ARTICLE are leaves - they
	// carry contentType/accessTier/assets/isbn and go through ingest. JOURNAL/VOLUME/ISSUE are
	// pure containers with no content of their own.
	private WorkType workType;

	// The parent's id, or null for a standalone BOOK/JOURNAL. A container carries no childIds
	// array back - the child points up via parentId, the same rule already used for
	// BookCollection: membership lives on the item, so the relationship exists once and cannot
	// disagree with itself.
	private String parentId;

	// Ordering among the children of one parent (Volume 12 before Volume 13, Issue 3 before Issue
	// 4). Optional; absent items sort last.
	private Integer sequence;

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

	// For a leaf (BOOK/ARTICLE) this is the aggregate of every part of every asset - see
	// IngestProcessor's recompute rule. Always NONE for a container (JOURNAL/VOLUME/ISSUE), which
	// never goes through ingest.
	private ContentState contentState;
	private String contentError;
	private List<Asset> assets;

	private Instant createdAt;
	private Instant updatedAt;

	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Asset {

		private ContentType format;
		private String mimeType;
		private boolean encrypted;
		private String keyId;

		// Wrapped once, on the first part of this asset ever locked, then shared unchanged by
		// every later part - one BEK per asset, not per part. Never leaves the server.
		private String masterWrappedBek;

		private List<Part> parts;

	}

	/** One chapter's own file: its own storage, its own index, its own ingest state. */
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Part {

		// 1-based, unique within an Asset - not an array index, so re-ingesting part 2 alone
		// never shifts what "part 3" means.
		private int partNumber;
		private String title;

		private long sizeBytes;
		private long cipherLength;
		private boolean hasSearchIndex;
		private int indexTerms;
		private String indexSkipReason;

		// Never leave the server; excluded from any future DTO.
		private String storageKey;
		private String indexKey;

		private ContentState contentState;
		private String contentError;
		private Instant updatedAt;

	}

}
