package com.tf.reader.ingest.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.ingest.index.BuiltSearchIndex;
import com.tf.reader.ingest.index.NoTextLayerException;
import com.tf.reader.ingest.index.SearchIndexService;

import lombok.RequiredArgsConstructor;

/**
 * Builds a search index for one chapter, updating that part's hasSearchIndex/indexTerms/
 * indexSkipReason as it goes. Shared by both the locked and unlocked ingest paths - a search
 * index is built for every PDF/EPUB part regardless of tier, not only SUBSCRIPTION/ELITE. Audio
 * has no text to index, and a scanned PDF with no usable text layer reaches READY without one;
 * both are "no index," never a failure, whichever path the part took to get here.
 */
@Component
@RequiredArgsConstructor
class SearchIndexBuilder {

	private final SearchIndexService searchIndexService;

	Optional<BuiltSearchIndex> build(String itemId, ContentType contentType, byte[] plaintext,
			CatalogueItem.Part part) {
		if (contentType == ContentType.AUDIO) {
			return Optional.empty();
		}
		try {
			BuiltSearchIndex built = contentType == ContentType.PDF
					? searchIndexService.buildPdfIndex(itemId, plaintext)
					: searchIndexService.buildEpubIndex(itemId, plaintext);
			part.setHasSearchIndex(true);
			part.setIndexTerms(built.termCount());
			return Optional.of(built);
		}
		catch (NoTextLayerException notSearchable) {
			// Not a failure - a scanned chapter that still reaches READY, just without an index.
			part.setHasSearchIndex(false);
			part.setIndexSkipReason(notSearchable.getMessage());
			return Optional.empty();
		}
	}

}
