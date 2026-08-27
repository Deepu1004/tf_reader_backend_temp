package com.tf.reader.ingest.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tf.reader.sync.model.Locator;

/**
 * PDF counterpart of {@code EpubCfiGeneratorTest}. There is no CFI golden for PDF (see
 * {@link PdfIndexExtractor}), so this pins the structural contract the mobile {@code queryIndex} depends
 * on, over the SAME sample PDF the mobile {@code searchPdf.test.ts} uses. The sample is three
 * near-identical pages ("TF Reader sample PDF - page N of 3 / This file is GENERATED ... page through
 * all 3 pages"), so "generated" occurs once per page, "page" on every page, and "sample pdf" is adjacent
 * on every page while a reversed "pdf sample" never is.
 */
class PdfIndexExtractorTest {

	private static final String PDF = "/ingest/sample-plaintext.pdf";

	@Test
	void emitsPdfLocatorsWithPageChapterIdsInReadingOrder() throws IOException {
		List<IndexEntry> entries = extract();

		assertThat(entries).isNotEmpty();
		for (IndexEntry entry : entries) {
			Locator loc = entry.locator();
			assertThat(loc.getType()).isEqualTo(Locator.TYPE_PDF);
			assertThat(loc.getPage()).isBetween(1, 3);
			assertThat(loc.getOffset()).isNotNull().isGreaterThanOrEqualTo(0);
			assertThat(entry.chapterId()).isEqualTo("p" + loc.getPage());
			assertThat(entry.snippet().toLowerCase()).contains(entry.word());
		}
		assertReadingOrder(entries);
		assertSeqIsDense(entries);
	}

	@Test
	void recursOncePerPageForWordsOnEveryPage() throws IOException {
		List<IndexEntry> entries = extract();

		assertThat(countWord(entries, "generated")).isEqualTo(3);
		assertThat(distinctPagesFor(entries, "page")).containsExactly(1, 2, 3);
	}

	@Test
	void keepsPhraseWordsAdjacentInSeq() throws IOException {
		// "TF Reader sample PDF" -> "sample" immediately followed by "pdf", on every page.
		List<IndexEntry> entries = extract();

		assertThat(hasAdjacentPair(entries, "sample", "pdf")).isTrue();
		assertThat(hasAdjacentPair(entries, "pdf", "sample")).isFalse(); // order matters
	}

	@Test
	void acceptsTheBornDigitalSamplePdf() throws IOException {
		// The sample has text on all 3 pages, so the scanned-book guard must not trip.
		assertThat(new PdfIndexExtractor().extract(readBytes())).isNotEmpty();
	}

	@Test
	void scannedBookGuardRejectsPagesWithoutText() {
		// Born-digital: text on every / most pages -> usable.
		assertThat(PdfIndexExtractor.hasUsableTextLayer(50, 50)).isTrue();
		assertThat(PdfIndexExtractor.hasUsableTextLayer(200, 180)).isTrue(); // text book + scanned plates
		// Scanned / image-only: no or negligible text -> rejected.
		assertThat(PdfIndexExtractor.hasUsableTextLayer(300, 0)).isFalse();
		assertThat(PdfIndexExtractor.hasUsableTextLayer(300, 5)).isFalse(); // stray page numbers only
		// Edge: an empty document is not usable.
		assertThat(PdfIndexExtractor.hasUsableTextLayer(0, 0)).isFalse();
	}

	// --- helpers -------------------------------------------------------------

	private List<IndexEntry> extract() throws IOException {
		return new PdfIndexExtractor().extract(readBytes());
	}

	/** seq is the token ordinal in reading order: 0..n-1, dense, in list order. */
	private void assertSeqIsDense(List<IndexEntry> entries) {
		for (int i = 0; i < entries.size(); i++) {
			assertThat(entries.get(i).seq()).as("seq at %d", i).isEqualTo(i);
		}
	}

	/** (page, offset) is non-decreasing across the whole book — grouping needs no later sort. */
	private void assertReadingOrder(List<IndexEntry> entries) {
		for (int i = 1; i < entries.size(); i++) {
			Locator prev = entries.get(i - 1).locator();
			Locator cur = entries.get(i).locator();
			boolean nonDecreasing = prev.getPage() < cur.getPage()
					|| (prev.getPage().equals(cur.getPage()) && prev.getOffset() <= cur.getOffset());
			assertThat(nonDecreasing).as("reading order at %d", i).isTrue();
		}
	}

	private long countWord(List<IndexEntry> entries, String word) {
		return entries.stream().filter(e -> e.word().equals(word)).count();
	}

	private List<Integer> distinctPagesFor(List<IndexEntry> entries, String word) {
		return entries.stream()
				.filter(e -> e.word().equals(word))
				.map(e -> e.locator().getPage())
				.distinct()
				.sorted()
				.toList();
	}

	/** True if {@code first} is immediately followed by {@code second} on the same page, anywhere. */
	private boolean hasAdjacentPair(List<IndexEntry> entries, String first, String second) {
		for (int i = 0; i + 1 < entries.size(); i++) {
			IndexEntry a = entries.get(i);
			IndexEntry b = entries.get(i + 1);
			if (a.word().equals(first) && b.word().equals(second)
					&& a.locator().getPage().equals(b.locator().getPage())) {
				return true;
			}
		}
		return false;
	}

	private byte[] readBytes() throws IOException {
		try (InputStream in = PdfIndexExtractorTest.class.getResourceAsStream(PDF)) {
			assertThat(in).as("sample PDF fixture present").isNotNull();
			return in.readAllBytes();
		}
	}
}
