package com.tf.reader.ingest.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tf.reader.ingest.index.Tokenizer.Token;

/**
 * The tokenizer must match the mobile reference ({@code text.ts}) exactly — a divergence here shifts
 * every {@code :offset} and {@code seq} and breaks CFI conformance downstream. Each case pins a rule
 * the reference defines, not an incidental behaviour.
 */
class TokenizerTest {

	@Test
	void lowercasesTokensAndRecordsTheirSourceOffset() {
		List<Token> tokens = Tokenizer.tokenize("Chapter One");

		assertThat(tokens).containsExactly(new Token("chapter", 0), new Token("one", 8));
	}

	@Test
	void splitsOnEveryNonAlphanumericSoPunctuationIsNeverPartOfAWord() {
		// "don't" -> don,t and "epub.js" -> epub,js — deterministic over-split, matching the reference.
		List<Token> tokens = Tokenizer.tokenize("don't epub.js");

		assertThat(tokens.stream().map(Token::word)).containsExactly("don", "t", "epub", "js");
	}

	@Test
	void returnsNoTokensForTextWithNoAlphanumericRuns() {
		assertThat(Tokenizer.tokenize("  —  ...  ")).isEmpty();
	}

	@Test
	void trimsSnippetInwardToWordBoundariesSoItNeverStartsOrEndsMidWord() {
		String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu";
		int start = text.indexOf("epsilon");
		int end = start + "epsilon".length();

		String snippet = Tokenizer.makeSnippet(text, start, end);

		assertThat(snippet).contains("epsilon");
		assertThat(snippet).doesNotStartWith(" ").doesNotEndWith(" ");
		assertThat(text).contains(snippet);
		// Padded ~40 chars each side but cut at boundaries, so no partial leading/trailing word.
		assertThat(snippet.split(" ")).allSatisfy(word -> assertThat(word).isNotEmpty());
	}

	@Test
	void clampsSnippetToTheTextBoundsWhenTheHitIsAtTheStart() {
		String text = "opening words of the passage";
		String snippet = Tokenizer.makeSnippet(text, 0, "opening".length());

		// The left pad runs off the front; out-of-range is treated as a non-word char, so it clamps
		// to index 0 rather than throwing.
		assertThat(snippet).isEqualTo(text);
	}
}
