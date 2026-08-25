package com.tf.reader.ingest.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Conformance smoke check: the Java builder must reproduce epub.js's CFIs. The full byte-for-byte
 * golden (2,139 tokens, produced by the mobile oracle) is NOT committed — this keeps the two
 * device-verified anchor CFIs, which break first if the CFI base or the in-document stepping is wrong.
 * To restore full-corpus conformance, regenerate the golden from the mobile oracle and diff against it.
 */
class EpubCfiGeneratorTest {

	private static final String EPUB = "/ingest/sample-plaintext.epub";

	@Test
	void reproducesTheDeviceVerifiedAnchorCfis() throws IOException {
		// Ahana verified these two on-device; if the base or the stepping is wrong they break first.
		List<String> cfis = new EpubIndexExtractor().extract(readBytes(EPUB))
				.stream().map(entry -> entry.locator().getCfi()).toList();

		assertThat(cfis).contains("epubcfi(/6/2[ch1]!/4/4/1:0)", "epubcfi(/6/2[ch1]!/4/4/1:113)");
	}

	private byte[] readBytes(String path) throws IOException {
		try (InputStream in = EpubCfiGeneratorTest.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}
}
