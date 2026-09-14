package com.tf.reader.ingest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.tf.reader.catalogue.entity.CatalogueItem;
import com.tf.reader.catalogue.entity.ContentType;
import com.tf.reader.ingest.index.BuiltSearchIndex;
import com.tf.reader.ingest.index.NoTextLayerException;
import com.tf.reader.ingest.index.SearchIndexService;

/**
 * Wiring only - {@code SearchIndexService} is already tested on its own. This proves
 * {@code SearchIndexBuilder} routes to the right builder method by format, updates the part's
 * index flags correctly, skips audio outright, and turns a scanned PDF into "no index" rather
 * than a failure - shared logic used by both the locked and unlocked ingest paths.
 */
class SearchIndexBuilderTest {

	private final SearchIndexService searchIndexService = mock(SearchIndexService.class);
	private final SearchIndexBuilder builder = new SearchIndexBuilder(searchIndexService);

	private static CatalogueItem.Part part() {
		return new CatalogueItem.Part();
	}

	@Test
	void buildsAPdfIndexAndMarksThePart() {
		CatalogueItem.Part part = part();
		when(searchIndexService.buildPdfIndex("item_1", "plain".getBytes()))
				.thenReturn(new BuiltSearchIndex("index-json".getBytes(), 42));

		Optional<BuiltSearchIndex> result = builder.build("item_1", ContentType.PDF, "plain".getBytes(), part);

		assertThat(result).isPresent();
		assertThat(part.isHasSearchIndex()).isTrue();
		assertThat(part.getIndexTerms()).isEqualTo(42);
	}

	@Test
	void buildsAnEpubIndexAndMarksThePart() {
		CatalogueItem.Part part = part();
		when(searchIndexService.buildEpubIndex("item_1", "plain".getBytes()))
				.thenReturn(new BuiltSearchIndex("index-json".getBytes(), 7));

		Optional<BuiltSearchIndex> result = builder.build("item_1", ContentType.EPUB, "plain".getBytes(), part);

		assertThat(result).isPresent();
		assertThat(part.isHasSearchIndex()).isTrue();
		assertThat(part.getIndexTerms()).isEqualTo(7);
	}

	@Test
	void skipsAudioOutrightWithoutCallingSearchIndexService() {
		CatalogueItem.Part part = part();

		Optional<BuiltSearchIndex> result = builder.build("item_1", ContentType.AUDIO, "plain".getBytes(), part);

		assertThat(result).isEmpty();
		assertThat(part.isHasSearchIndex()).isFalse();
		verify(searchIndexService, never()).buildPdfIndex(any(), any());
		verify(searchIndexService, never()).buildEpubIndex(any(), any());
	}

	@Test
	void aScannedPdfIsNoIndexNotAFailure() {
		CatalogueItem.Part part = part();
		NoTextLayerException scanned = mock(NoTextLayerException.class);
		when(scanned.getMessage()).thenReturn("only 2 of 40 pages carried extractable text");
		when(searchIndexService.buildPdfIndex(any(), any())).thenThrow(scanned);

		Optional<BuiltSearchIndex> result = builder.build("item_1", ContentType.PDF, "plain".getBytes(), part);

		assertThat(result).isEmpty();
		assertThat(part.isHasSearchIndex()).isFalse();
		assertThat(part.getIndexSkipReason()).isNotBlank();
	}

}
