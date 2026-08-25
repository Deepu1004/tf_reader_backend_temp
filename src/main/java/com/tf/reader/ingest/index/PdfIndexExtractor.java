package com.tf.reader.ingest.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.tf.reader.sync.model.Locator;

/**
 * Emits one {@link IndexEntry} per token from a PDF, page by page in reading order, each carrying a
 * {@code {type:PDF, page, offset}} locator. The PDF counterpart of {@link EpubIndexExtractor}; mirrors
 * the mobile reference ({@code src/features/search/extractor.ts} createPdfExtractor). The pipeline below
 * the extractor (grouping in {@link BookSearchIndex}, and the mobile query/adjacency) is already
 * format-agnostic, so this is the one PDF-specific stage.
 *
 * <p>Unlike EPUB there is no CFI and NO conformance golden. {@code offset} is the char offset within
 * THIS page's extracted text and is used only for snippet cutting and intra-page reading order; phrase
 * adjacency rides on {@code seq} (the global token ordinal), not on the offset. So the offsets need only
 * be self-consistent within one index — they are deliberately NOT required to match the mobile pdf.js
 * extractor char-for-char, which is unachievable since PDFBox's text model differs from pdf.js's. The
 * Reader navigates a PDF hit by PAGE ({@code goTo(page)}); the offset is never resolved on device.
 *
 * <p>A PDF has no chapters, so {@code chapterId} is the page id {@code p<N>} — the key the mobile
 * {@code useBookSearch} already assumes for a PDF hit.
 *
 * <p>Needs PDFBox, a new dependency greenlit by wokay: add {@code org.apache.pdfbox:pdfbox} (3.0.x) to
 * the pom. Text extraction only — no rendering, no fonts.
 */
final class PdfIndexExtractor {

	/**
	 * A book with fewer than this fraction of pages carrying any text is treated as scanned/image-only
	 * and rejected ({@link NoTextLayerException}). Born-digital books extract text on ~100% of pages; a
	 * scan yields ~0 (a stray page number here and there). 10% tolerates a text book with a block of
	 * scanned plates while still catching a book that is scans with only an index/preface as real text.
	 */
	private static final int MIN_TEXT_PAGE_PERCENT = 10;

	List<IndexEntry> extract(byte[] pdfBytes) {
		List<IndexEntry> entries = new ArrayList<>();
		int pageCount;
		int pagesWithText = 0;
		try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			pageCount = doc.getNumberOfPages();
			int seq = 0;
			// Pages are 1-based in the PDF and in the Locator; walk them in order so grouping keeps
			// reading order with no later sort (same contract as the EPUB side). One page per pass so
			// each token's offset is relative to its own page's text, matching the mobile extractor.
			for (int page = 1; page <= pageCount; page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				String pageText = stripper.getText(doc);
				String chapterId = "p" + page;
				boolean pageHadText = false;
				for (Tokenizer.Token token : Tokenizer.tokenize(pageText)) {
					int end = token.offset() + token.word().length();
					String snippet = Tokenizer.makeSnippet(pageText, token.offset(), end);
					entries.add(new IndexEntry(
							seq++, token.word(), chapterId, Locator.pdf(page, token.offset()), snippet));
					pageHadText = true;
				}
				if (pageHadText) {
					pagesWithText++;
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("failed to read PDF for search indexing", e);
		}
		// A scanned/image-only book extracts (near-)nothing; refuse it rather than ship an empty index
		// that makes search silently return nothing. See NoTextLayerException.
		if (!hasUsableTextLayer(pageCount, pagesWithText)) {
			throw new NoTextLayerException(pageCount, pagesWithText);
		}
		return entries;
	}

	/** Pure so the scanned-book threshold is unit-testable without a scanned fixture. */
	static boolean hasUsableTextLayer(int pageCount, int pagesWithText) {
		return pageCount > 0 && pagesWithText * 100 >= pageCount * MIN_TEXT_PAGE_PERCENT;
	}
}
