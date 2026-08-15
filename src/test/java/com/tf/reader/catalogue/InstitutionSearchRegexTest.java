package com.tf.reader.catalogue;

import com.tf.reader.catalogue.repository.InstitutionSearchRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search pattern, on its own. No Mongo.
 *
 * <p>This is the single most likely defect in the endpoint, so it gets its own class. Two failure
 * modes, both silent: a prefix that does not match, which makes type-ahead look broken, and an
 * unescaped metacharacter, which turns a search box into a regular expression a stranger controls.
 *
 * <p>The patterns are evaluated here with {@link Pattern}, which is Java's engine rather than the
 * PCRE2 engine MongoDB uses. The constructs involved, a literal alternation and backslash escapes,
 * mean the same thing in both, which is exactly why the implementation avoids {@code \Q...\E}: that
 * one does not.
 */
class InstitutionSearchRegexTest {

    private static boolean matches(String term, String text) {
        return Pattern.compile(InstitutionSearchRepository.prefixPattern(term), Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }

    @ParameterizedTest(name = "q=[{0}] against \"{1}\" -> {2}")
    @CsvSource({
        // the case the whole endpoint exists for
        "impe,       Imperial College London,   true",
        "imperial,   Imperial College London,   true",
        "IMPE,       Imperial College London,   true",
        "impe,       imperial college london,   true",

        // a later word matches, because a user types the word they remember
        "college,    Imperial College London,   true",
        "lond,       Imperial College London,   true",

        // but a fragment in the middle of a word does not
        "mperial,    Imperial College London,   false",
        "ondon,      Imperial College London,   false",

        // and a different institution does not
        "impe,       University of Leeds,       false",
        "leeds,      University of Leeds,       true",
    })
    @DisplayName("a partial word matches from the start of any word, and nowhere else")
    void prefixMatching(String term, String text, boolean expected) {
        // Anchoring to a word boundary rather than matching anywhere is what keeps results
        // recognisable. An unanchored search for "don" returning "London" reads like a bug.
        assertThat(matches(term, text)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a MongoDB $text index could not do this, which is why one is not used")
    void whyNotText() {
        // The contract's own example is q=imp matching Imperial. Person B's entity has @TextIndexed on
        // name, and a text index matches whole words, so it cannot serve that. Recorded as a test
        // because it is the assumption most likely to be re-made.
        assertThat(matches("impe", "Imperial College London")).isTrue();
        assertThat("Imperial College London".split("\\s+"))
                .as("no whole word equals the search term, so $text would return no rows")
                .noneSatisfy(word -> assertThat(word).isEqualToIgnoringCase("impe"));
    }

    @ParameterizedTest(name = "a term containing [{0}] is treated as text")
    @MethodSource("metacharacters")
    @DisplayName("every regex metacharacter is escaped, so a search box is not a query language")
    void metacharactersAreEscaped(char meta) {
        String term = "imp" + meta + "erial";

        // It must not blow up, and it must not match a name that does not literally contain the
        // character. An unescaped "." would make "imp.erial" match "Imperial", which is the mild
        // version; an unescaped "(" is an unbalanced group and throws on the server.
        assertThat(matches(term, "Imperial College London"))
                .as("[%s] must be matched literally, not interpreted", meta)
                .isFalse();
        assertThat(matches(term, "imp" + meta + "erial College"))
                .as("[%s] must still match a name that really contains it", meta)
                .isTrue();
    }

    static List<Character> metacharacters() {
        return List.of('\\', '^', '$', '.', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}');
    }

    @Test
    @DisplayName("the classic injection attempts return nothing rather than everything")
    void doesNotMatchEverything() {
        for (String hostile : List.of(".*", ".+", "^", "|", "(a|b)*", "[a-z]+", "a{1,99}")) {
            assertThat(matches(hostile, "Imperial College London"))
                    .as("q=%s must not match an unrelated institution", hostile)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Pattern.quote is not used, and the pattern says so")
    void doesNotUseQuoteBlocks() {
        // \Q...\E is Java's construct handed to the server's engine. A user typing \E would then change
        // how the rest of their own input is parsed.
        assertThat(InstitutionSearchRepository.prefixPattern("impe")).doesNotContain("\\Q", "\\E");
        assertThat(InstitutionSearchRepository.prefixPattern("impe")).isEqualTo("(^|\\s)impe");
    }

    @Test
    @DisplayName("the escape list covers every metacharacter the tests exercise")
    void escapeListIsComplete() {
        // Guards the two lists drifting apart: if somebody adds a character to the implementation's
        // list without a test, or removes one that a test still relies on, this fails.
        assertThat(InstitutionSearchRepository.metacharacters())
                .containsExactlyInAnyOrderElementsOf(metacharacters());
    }
}