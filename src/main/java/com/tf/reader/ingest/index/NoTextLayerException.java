package com.tf.reader.ingest.index;

/**
 * Thrown when a PDF has effectively no extractable text — a scanned / image-only book, where the pages
 * are pictures of text and no font-backed characters exist to index. Both PDFBox and pdf.js return
 * (near-)nothing for such a book, so this is a property of the source, not a builder failure.
 *
 * <p>The point is to fail LOUDLY rather than ship an empty index that makes search look broken (every
 * query silently returns nothing). wokay's ingestion should catch this and mark the book
 * not-text-searchable (e.g. hide the search box for it). Making such a book searchable would require
 * OCR — a separate, heavier capability that is deliberately out of scope here.
 */
public class NoTextLayerException extends RuntimeException {

	private final int pageCount;
	private final int pagesWithText;

	NoTextLayerException(int pageCount, int pagesWithText) {
		super("PDF has no usable text layer (likely scanned/image-only): only " + pagesWithText
				+ " of " + pageCount + " pages carried extractable text — not text-searchable without OCR");
		this.pageCount = pageCount;
		this.pagesWithText = pagesWithText;
	}

	public int getPageCount() {
		return pageCount;
	}

	public int getPagesWithText() {
		return pagesWithText;
	}
}
