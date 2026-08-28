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
 * Builds a search index for a book, updating the asset's hasSearchIndex/indexTerms/
 * indexSkipReason as it goes. Shared by both the locked and unlocked ingest paths - a search
 * index is built for every PDF/EPUB regardless of tier, not only SUBSCRIPTION/ELITE. Audio has no
 * text to index, and a scanned PDF with no usable text layer reaches READY without one; both are
 * "no index," never a failure, whichever path the book took to get here.
 */
@Component
@RequiredArgsConstructor
class SearchIndexBuilder {

	private final SearchIndexService searchIndexService;

	Optional<BuiltSearchIndex> build(String itemId, ContentType contentType, byte[] plaintext,
			CatalogueItem.Asset asset) {
		if (contentType == ContentType.AUDIO) {
			return Optional.empty();
		}
		try {
			BuiltSearchIndex built = contentType == ContentType.PDF ? searchIndexService.buildPdfIndex(itemId, plaintext)
					: searchIndexService.buildEpubIndex(itemId, plaintext);
			asset.setHasSearchIndex(true);
			asset.setIndexTerms(built.termCount());
			return Optional.of(built);
		}
		catch (NoTextLayerException notSearchable) {
			// Not a failure - a scanned book that still reaches READY, just without an index.
			asset.setHasSearchIndex(false);
			asset.setIndexSkipReason(notSearchable.getMessage());
			return Optional.empty();
		}
	}

}
