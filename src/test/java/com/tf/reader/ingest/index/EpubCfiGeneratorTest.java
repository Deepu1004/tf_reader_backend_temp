package com.tf.reader.ingest.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonPropertyOrder; // annotations stayed com.fasterxml in Jackson 3
import tools.jackson.databind.ObjectMapper;

/**
 * Conformance: the Java builder must reproduce epub.js's CFIs exactly. The golden JSONL (frozen by the
 * mobile oracle) is the normative spec — when this prose and the golden disagree, the golden wins. A
 * single divergent line points at the exact token whose stepping or offset is wrong.
 */
class EpubCfiGeneratorTest {

	private static final String EPUB = "/ingest/sample-plaintext.epub";
	private static final String GOLDEN = "/ingest/sample-plaintext.cfi-golden.jsonl";

	/** The golden line shape: same fields, same order as the oracle's output. */
	@JsonPropertyOrder({"seq", "word", "chapterId", "cfi", "snippet"})
	private record GoldenLine(int seq, String word, String chapterId, String cfi, String snippet) {
	}

	@Test
	void reproducesTheEpubJsGoldenLineForLine() throws IOException {
		List<String> golden = readLines(GOLDEN);
		List<String> actual = buildGoldenLines();

		assertThat(actual).hasSameSizeAs(golden);
		for (int i = 0; i < golden.size(); i++) {
			assertThat(actual.get(i)).as("golden line %d", i).isEqualTo(golden.get(i));
		}
	}

	@Test
	void reproducesTheDeviceVerifiedAnchorCfis() throws IOException {
		// Ahana verified these two on-device; if the base or the stepping is wrong they break first.
		List<String> cfis = new EpubIndexExtractor().extract(readBytes(EPUB))
				.stream().map(entry -> entry.locator().getCfi()).toList();

		assertThat(cfis).contains("epubcfi(/6/2[ch1]!/4/4/1:0)", "epubcfi(/6/2[ch1]!/4/4/1:113)");
	}

	private List<String> buildGoldenLines() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		List<String> lines = new ArrayList<>();
		for (IndexEntry entry : new EpubIndexExtractor().extract(readBytes(EPUB))) {
			lines.add(mapper.writeValueAsString(new GoldenLine(
					entry.seq(), entry.word(), entry.chapterId(), entry.locator().getCfi(), entry.snippet())));
		}
		return lines;
	}

	private byte[] readBytes(String path) throws IOException {
		try (InputStream in = EpubCfiGeneratorTest.class.getResourceAsStream(path)) {
			return in.readAllBytes();
		}
	}

	private List<String> readLines(String path) throws IOException {
		return new String(readBytes(path), StandardCharsets.UTF_8).lines().toList();
	}
}
