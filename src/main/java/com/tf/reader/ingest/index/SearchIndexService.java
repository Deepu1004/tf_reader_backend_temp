package com.tf.reader.ingest.index;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The seam wokay's ingestion calls: book bytes in, the serialized (plaintext) search index plus its
 * distinct-term count out. Encryption, storage and bundling of the returned bytes are wokay's — to
 * them the index is an opaque blob, understood only here and by the mobile {@code queryIndex}.
 *
 * <p>Both formats are handled: {@link #buildEpubIndex} (EPUB, CFI locators) and {@link #buildPdfIndex}
 * (PDF, page/offset locators). The one {@code Extractor} per format is the only format-aware stage;
 * grouping and serialization below are shared.
 *
 * <p>Uses the Spring-managed Jackson 3 {@code tools.jackson.databind.ObjectMapper} — the bean this
 * Boot 4 app actually exposes (the Jackson 2 {@code com.fasterxml} mapper has no bean here).
 */
@Service
@RequiredArgsConstructor
public class SearchIndexService {

	private final ObjectMapper objectMapper;
	private final EpubIndexExtractor epubExtractor = new EpubIndexExtractor();
	private final PdfIndexExtractor pdfExtractor = new PdfIndexExtractor();

	public BuiltSearchIndex buildEpubIndex(String bookId, byte[] epubBytes) {
		return build(bookId, BookSearchIndex.FORMAT_EPUB, epubExtractor.extract(epubBytes));
	}

	/**
	 * Throws {@link NoTextLayerException} for a scanned/image-only PDF (no extractable text) — catch it
	 * and mark the book not-text-searchable rather than shipping an empty index.
	 */
	public BuiltSearchIndex buildPdfIndex(String bookId, byte[] pdfBytes) {
		return build(bookId, BookSearchIndex.FORMAT_PDF, pdfExtractor.extract(pdfBytes));
	}

	private BuiltSearchIndex build(String bookId, String format, List<IndexEntry> entries) {
		BookSearchIndex index = BookSearchIndex.fromEntries(bookId, format, entries);
		try {
			return new BuiltSearchIndex(objectMapper.writeValueAsBytes(index), index.index().size());
		} catch (JacksonException e) {
			throw new IllegalStateException("failed to serialize search index for " + bookId, e);
		}
	}
}
