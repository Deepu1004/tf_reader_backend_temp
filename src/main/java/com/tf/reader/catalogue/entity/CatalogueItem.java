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

	@Indexed(sparse = true)
	private String isbn;

	private String language;

	@TextIndexed
	private String description;

	@TextIndexed
	private List<String> subjects;

	private LocalDate publishedAt;


	private Integer numberOfPages;

	private Integer duration;

	private String coverUrl;
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
