package com.tf.reader.catalogue;

import com.tf.reader.catalogue.service.InstitutionQueryService.ListRequest;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parameter handling, on its own. No Spring, no Mongo, no Docker.
 *
 * <p>Every case here is something a real client sends. {@code q} comes straight from a text field, so
 * stray spaces and mixed case arrive on day one, and page and size are whatever the previous
 * response's paging code computed, including when it computed them wrongly.
 */
class InstitutionListRequestTest {

    @Test
    @DisplayName("no parameters is a valid request for the first page")
    void defaultsMatchTheContract() {
        ListRequest request = ListRequest.of(null, null, null, null);

        assertThat(request.q()).isNull();
        assertThat(request.country()).isNull();
        assertThat(request.page()).isZero();
        assertThat(request.size()).isEqualTo(20);
    }

    @ParameterizedTest(name = "q [{0}] becomes [{1}]")
    @CsvSource(
            nullValues = "NULL",
            value = {"'  Imperial  ', Imperial", "imperial, imperial", "'   ', NULL", "'', NULL", "NULL, NULL"})
    @DisplayName("q is trimmed, and blank means no filter rather than a filter matching nothing")
    void qIsTrimmed(String input, String expected) {
        assertThat(ListRequest.of(input, null, null, null).q()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "country [{0}] becomes [{1}]")
    @CsvSource(nullValues = "NULL", value = {"uk, uk", "UK, UK", "'  uk  ', uk", "'  ', NULL"})
    @DisplayName("country is only trimmed here; matching it case insensitively is the query's job")
    void countryIsTrimmedNotUpperCased(String input, String expected) {
        // An earlier draft upper-cased it. That works for codes like UK and breaks the moment somebody
        // stores "United Kingdom", which the contract also allows.
        assertThat(ListRequest.of(null, input, null, null).country()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "size={0} is rejected")
    @ValueSource(ints = {0, -3, 101, 5000, Integer.MAX_VALUE})
    @DisplayName("size outside 1 to 100 is a 400, with the contract's own message")
    void sizeOutOfRangeIsRejected(int size) {
        // Rejected, not clamped. Quietly serving 100 rows to a client that asked for 5000 is a paging
        // bug the client cannot see, and the contract documents the 400.
        assertThatThrownBy(() -> ListRequest.of(null, null, null, size))
                .isInstanceOf(ApiException.class)
                .hasMessage("size must be between 1 and 100")
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @ParameterizedTest(name = "size={0} is accepted")
    @ValueSource(ints = {1, 20, 100})
    @DisplayName("the range boundaries are inclusive")
    void sizeInRangeIsAccepted(int size) {
        assertThat(ListRequest.of(null, null, null, size).size()).isEqualTo(size);
    }

    @ParameterizedTest(name = "page={0} is rejected")
    @ValueSource(ints = {-1, -20, Integer.MIN_VALUE})
    @DisplayName("a negative page is a client bug, so it fails loudly")
    void negativePageIsRejected(int page) {
        assertThatThrownBy(() -> ListRequest.of(null, null, page, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("page must be zero or greater")
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("a page past the end is a valid request, not an error")
    void largePageIsAccepted() {
        // It returns 200 with an empty array and a correct total.
        assertThat(ListRequest.of(null, null, 9_999, null).page()).isEqualTo(9_999);
    }

    @Test
    @DisplayName("of() normalises everything at once")
    void normalisationHappensInOnePlace() {
        assertThat(ListRequest.of("  Imperial  ", "  uk  ", 2, 50))
                .isEqualTo(new ListRequest("Imperial", "uk", 2, 50));
    }
}