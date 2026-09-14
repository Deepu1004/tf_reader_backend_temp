package com.tf.reader.ingest.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link EpubIndexExtractor} skips {@code text/html} chapters and only indexes
 * {@code application/xhtml+xml} chapters.
 */
class EpubIndexExtractorTest {

	@Test
	void skipsTextHtmlChapters() throws IOException {
		byte[] epub = buildEpub("<p>xhtmltoken</p>", "<p>htmlskip</p>");
		List<String> words = new EpubIndexExtractor().extract(epub)
				.stream().map(IndexEntry::word).toList();
		assertThat(words).contains("xhtmltoken").doesNotContain("htmlskip");
	}

	private byte[] buildEpub(String xhtmlBody, String htmlBody) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			write(zip, "META-INF/container.xml", container());
			write(zip, "OEBPS/content.opf", opf());
			write(zip, "OEBPS/ch1.xhtml", xhtml(xhtmlBody));
			write(zip, "OEBPS/ch2.html", htmlBody.getBytes());
		}
		return out.toByteArray();
	}

	private void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	private byte[] container() {
		return """
				<?xml version="1.0"?>
				<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
				  <rootfiles>
				    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
				  </rootfiles>
				</container>""".getBytes();
	}

	private byte[] opf() {
		return """
				<?xml version="1.0" encoding="UTF-8"?>
				<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
				  <metadata/>
				  <manifest>
				    <item id="ch1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
				    <item id="ch2" href="ch2.html" media-type="text/html"/>
				  </manifest>
				  <spine>
				    <itemref idref="ch1"/>
				    <itemref idref="ch2"/>
				  </spine>
				</package>""".getBytes();
	}

	private byte[] xhtml(String body) {
		return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>" + body + "</body></html>")
				.getBytes();
	}
}
