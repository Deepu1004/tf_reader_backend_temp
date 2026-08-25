package com.tf.reader.ingest.index;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads an EPUB container: unzips it, parses the OPF, and returns the spine's chapter bodies in reading
 * order. Packaging only — the walk is {@link EpubIndexExtractor}, the CFI stepping {@link EpubCfiGenerator}.
 */
final class EpubArchive {

	/** One spine chapter: its idref (the CFI base's id assertion) and its parsed {@code <body>}. */
	record Chapter(String idref, Element body) {
	}

	private final Map<String, byte[]> files;
	private final Document opf;
	private final String opfDir;

	private EpubArchive(Map<String, byte[]> files, Document opf, String opfDir) {
		this.files = files;
		this.opf = opf;
		this.opfDir = opfDir;
	}

	static EpubArchive open(byte[] epubBytes) {
		Map<String, byte[]> files = unzip(epubBytes);
		Document container = parseXml(fileOrThrow(files, "META-INF/container.xml"));
		String opfPath = firstByLocalName(container, "rootfile").getAttribute("full-path");
		Document opf = parseXml(fileOrThrow(files, opfPath));
		return new EpubArchive(files, opf, parentPath(opfPath));
	}

	/** The {@code <spine>} position among {@code <package>}'s element children — the CFI's first step. */
	int spineElementIndex() {
		return elementIndexOf(opf.getDocumentElement(), "spine");
	}

	List<Chapter> chapters() {
		Map<String, String> hrefById = manifestHrefs();
		List<Chapter> chapters = new ArrayList<>();
		for (String idref : spineIdrefs()) {
			Document doc = parseXml(fileOrThrow(files, resolve(hrefById.get(idref))));
			chapters.add(new Chapter(idref, firstByLocalName(doc, "body")));
		}
		return chapters;
	}

	private Map<String, String> manifestHrefs() {
		Map<String, String> hrefById = new LinkedHashMap<>();
		NodeList items = opf.getElementsByTagNameNS("*", "item");
		for (int i = 0; i < items.getLength(); i++) {
			Element item = (Element) items.item(i);
			hrefById.put(item.getAttribute("id"), item.getAttribute("href"));
		}
		return hrefById;
	}

	private List<String> spineIdrefs() {
		List<String> idrefs = new ArrayList<>();
		NodeList itemrefs = opf.getElementsByTagNameNS("*", "itemref");
		for (int i = 0; i < itemrefs.getLength(); i++) {
			idrefs.add(((Element) itemrefs.item(i)).getAttribute("idref"));
		}
		return idrefs;
	}

	/** Resolve a manifest href against the OPF's directory. Prototype: no {@code ../} handling. */
	private String resolve(String href) {
		return opfDir.isEmpty() ? href : opfDir + "/" + href;
	}

	private static int elementIndexOf(Node parent, String localName) {
		int index = 0;
		for (Node sib = parent.getFirstChild(); sib != null; sib = sib.getNextSibling()) {
			if (sib.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}
			if (localName.equals(sib.getLocalName())) {
				return index;
			}
			index++;
		}
		throw new IllegalStateException("EPUB OPF has no <" + localName + "> element");
	}

	private static Element firstByLocalName(Document doc, String localName) {
		NodeList nodes = doc.getElementsByTagNameNS("*", localName);
		if (nodes.getLength() == 0) {
			throw new IllegalStateException("EPUB document has no <" + localName + "> element");
		}
		return (Element) nodes.item(0);
	}

	private static Map<String, byte[]> unzip(byte[] epubBytes) {
		Map<String, byte[]> files = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(epubBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					files.put(entry.getName(), zip.readAllBytes());
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("failed to read EPUB archive", e);
		}
		return files;
	}

	private static byte[] fileOrThrow(Map<String, byte[]> files, String path) {
		byte[] bytes = files.get(path);
		if (bytes == null) {
			throw new IllegalStateException("EPUB is missing required entry: " + path);
		}
		return bytes;
	}

	private static String parentPath(String path) {
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	private static Document parseXml(byte[] xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setCoalescing(true);
			factory.setExpandEntityReferences(false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
		} catch (Exception e) {
			throw new IllegalStateException("failed to parse XML in EPUB", e);
		}
	}
}
