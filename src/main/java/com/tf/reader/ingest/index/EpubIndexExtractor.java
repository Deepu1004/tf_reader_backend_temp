package com.tf.reader.ingest.index;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Node;

import com.tf.reader.sync.model.Locator;

/**
 * Walks each chapter body in reading order and emits an {@link IndexEntry} with an epub.js-compatible
 * CFI for every token. Packaging (unzip/OPF) is {@link EpubArchive}; the CFI stepping is
 * {@link EpubCfiGenerator}. Correctness is the conformance golden, not this prose — see
 * {@code JAVA_CFI_SPEC.md}.
 *
 * <p>TODO(vaishnavi, 2026-08-24): port the xhtml-vs-html parse-mode divergence guard (spec §3.3). It
 * needs an HTML5 parser (a new dependency, awaiting sign-off like PDFBox); until then only
 * parse-unambiguous EPUBs are safe.
 */
final class EpubIndexExtractor {

	List<IndexEntry> extract(byte[] epubBytes) {
		EpubArchive archive = EpubArchive.open(epubBytes);
		int spineElementIndex = archive.spineElementIndex();
		List<EpubArchive.Chapter> chapters = archive.chapters();

		List<IndexEntry> entries = new ArrayList<>();
		int[] seq = {0};
		for (int i = 0; i < chapters.size(); i++) {
			EpubArchive.Chapter chapter = chapters.get(i);
			String cfiBase = EpubCfiGenerator.spineBase(spineElementIndex, i, chapter.idref());
			walkBody(chapter.body(), chapter.idref(), cfiBase, entries, seq);
		}
		return entries;
	}

	/** Depth-first, document order: tokenize every text node under the body; recurse into elements. */
	private void walkBody(Node node, String chapterId, String cfiBase, List<IndexEntry> out, int[] seq) {
		for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() == Node.TEXT_NODE) {
				emitTokens(child, chapterId, cfiBase, out, seq);
			} else if (child.getNodeType() == Node.ELEMENT_NODE) {
				walkBody(child, chapterId, cfiBase, out, seq);
			}
		}
	}

	private void emitTokens(Node textNode, String chapterId, String cfiBase, List<IndexEntry> out, int[] seq) {
		String text = textNode.getNodeValue();
		for (Tokenizer.Token token : Tokenizer.tokenize(text)) {
			int end = token.offset() + token.word().length();
			String cfi = EpubCfiGenerator.cfiFor(cfiBase, textNode, token.offset());
			String snippet = Tokenizer.makeSnippet(text, token.offset(), end);
			out.add(new IndexEntry(seq[0]++, token.word(), chapterId, Locator.epub(cfi), snippet));
		}
	}
}
