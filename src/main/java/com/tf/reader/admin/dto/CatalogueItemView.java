package com.tf.reader.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tf.reader.catalogue.entity.AccessTier;
import com.tf.reader.catalogue.entity.ContentState;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.catalogue.entity.ItemStatus;


public record CatalogueItemView(String id, String publisherId, String publisherName, List<String> collectionIds,
		String title, String subtitle, List<String> authors, List<String> editors, List<String> narrators,
		String isbn, ContentType contentType, AccessTier accessTier, List<String> subjects, String language,
		String description, LocalDate publishedAt, Integer numberOfPages, Integer duration, String coverUrl,
		ItemStatus status, ContentState contentState, String contentError, List<Asset> assets, Instant createdAt,
		Instant updatedAt, String entitlementStatus) {


	public record Asset(ContentType format, String mimeType, long sizeBytes, Long cipherLength, boolean encrypted,
			boolean hasSearchIndex, String indexSkipReason, Integer indexTerms) {
	}
}
