package com.tf.reader.ingest.index;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Generates EPUB CFIs that match epub.js byte-for-byte. The client resolves CFIs with epub.js at read
 * time, so a CFI that does not match what epub.js would mint navigates nowhere; conformance against
 * the golden fixture ({@code JAVA_CFI_SPEC.md}) is the only definition of correct here, not this prose.
 *
 * <p>The stepping rule is the whole reason this is delicate: an element step is
 * {@code (elementIndexAmongElementSiblings + 1) * 2}; a text step is
 * {@code textIndexAmongTextSiblings * 2 + 1}; whitespace-only text nodes are counted, because dropping
 * one shifts every subsequent odd step.
 */
final class EpubCfiGenerator {

	private EpubCfiGenerator() {
	}

	/**
	 * cfiBase for a spine item — pure OPF arithmetic: {@code /<spineStep>/<itemrefStep>[idref]}.
	 * {@code spineElementIndex} is the {@code <spine>} position among {@code <package>}'s element
	 * children (2 in a standard OPF → step 6); {@code itemrefIndex} is the item's position in the spine.
	 */
	static String spineBase(int spineElementIndex, int itemrefIndex, String idref) {
		int spineStep = (spineElementIndex + 1) * 2;
		int itemrefStep = (itemrefIndex + 1) * 2;
		return "/" + spineStep + "/" + itemrefStep + "[" + idref + "]";
	}

	/**
	 * Full CFI for a token: cfiBase, the indirection into the content document, the in-document path,
	 * and the char offset — e.g. {@code epubcfi(/6/2[ch1]!/4/2/1:8)}.
	 */
	static String cfiFor(String cfiBase, Node textNode, int offset) {
		return "epubcfi(" + cfiBase + "!" + inDocumentPath(textNode) + ":" + offset + ")";
	}

	/**
	 * Path from the document root's child down to {@code node}. The root element ({@code <html>}) is the
	 * base after the indirection and is not itself stepped, so the first step selects among its children
	 * ({@code <body>} is {@code /4}). Built leaf-up, then read root-down.
	 */
	static String inDocumentPath(Node node) {
		Node root = node.getOwnerDocument().getDocumentElement();
		StringBuilder path = new StringBuilder();
		for (Node n = node; n != null && n != root; n = n.getParentNode()) {
			path.insert(0, stepFor(n));
		}
		return path.toString();
	}

	private static String stepFor(Node node) {
		if (node.getNodeType() == Node.TEXT_NODE) {
			return "/" + (textIndex(node) * 2 + 1);
		}
		Element element = (Element) node;
		String step = "/" + ((elementIndex(element) + 1) * 2);
		String id = element.getAttribute("id");
		return id.isEmpty() ? step : step + "[" + id + "]";
	}

	/** Index among the parent's ELEMENT children only — CFI numbers elements 2, 4, 6 in element order. */
	private static int elementIndex(Element element) {
		int index = 0;
		for (Node sib = element.getParentNode().getFirstChild(); sib != null; sib = sib.getNextSibling()) {
			if (sib == element) {
				return index;
			}
			if (sib.getNodeType() == Node.ELEMENT_NODE) {
				index++;
			}
		}
		return index;
	}

	/** Index among the parent's TEXT children only. Whitespace-only text nodes count (see class doc). */
	private static int textIndex(Node text) {
		int index = 0;
		for (Node sib = text.getParentNode().getFirstChild(); sib != null; sib = sib.getNextSibling()) {
			if (sib == text) {
				return index;
			}
			if (sib.getNodeType() == Node.TEXT_NODE) {
				index++;
			}
		}
		return index;
	}
}
