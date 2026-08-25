package com.tf.reader.ingest.index;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tokenization and snippet rules for the search index, ported from the mobile reference
 * ({@code src/features/search/text.ts}). Build and query must tokenize identically, so the mobile
 * app and this builder are two implementations of one rule; the CFI conformance golden only lines up
 * if {@code :offset} and {@code seq} are defined the same way here as there.
 *
 * <p>Rule: a token is a maximal run of {@code [A-Za-z0-9]}, lowercased; everything else separates.
 * No stemming, no possessive handling — {@code "don't"} becomes {@code don} + {@code t}. Deterministic
 * over clever, on purpose (see the mobile note); matching the reference is part of conformance.
 */
final class Tokenizer {

	/** One word occurrence: the normalized word and the char offset where it started in the source. */
	record Token(String word, int offset) {
	}

	private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");

	/** ~40 chars of context each side of a hit — matches SNIPPET_PAD in the reference. */
	private static final int SNIPPET_PAD = 40;

	private Tokenizer() {
	}

	/** Split {@code text} into normalized tokens, each carrying its char offset within {@code text}. */
	static List<Token> tokenize(String text) {
		List<Token> tokens = new ArrayList<>();
		Matcher m = WORD.matcher(text);
		while (m.find()) {
			tokens.add(new Token(m.group().toLowerCase(), m.start()));
		}
		return tokens;
	}

	/**
	 * Preview snippet around the hit spanning {@code [start, end)}, padded by ~{@link #SNIPPET_PAD}
	 * each side then trimmed inward to word boundaries so it never begins or ends mid-word. An index
	 * out of range is treated as a non-word char, exactly as the reference reads {@code text[i] ?? ''}.
	 */
	static String makeSnippet(String text, int start, int end) {
		int from = Math.max(0, start - SNIPPET_PAD);
		int to = Math.min(text.length(), end + SNIPPET_PAD);
		while (from < start && isWordAt(text, from - 1)) from++;
		while (to > end && isWordAt(text, to)) to--;
		return text.substring(from, to).trim();
	}

	private static boolean isWordAt(String text, int i) {
		if (i < 0 || i >= text.length()) return false;
		char c = text.charAt(i);
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
	}
}
