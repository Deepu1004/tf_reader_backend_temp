package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Locator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aNullLocatorIsValidForRowsWrittenBeforeTheFieldExisted() {
        ProgressRequest request = new ProgressRequest("progress-1", "user-001", "book-001", 31L, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void anEpubOffsetOfZeroIsValidBecauseEpubHasNoStableOffset() {
        Locator epub = Locator.epub("epubcfi(/6/14!/4/2/2)");
        ProgressRequest request = new ProgressRequest("progress-1", "user-001", "book-001", 0L, epub);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void anInconsistentNestedLocatorFailsValidationOnTheProgressRequestItself() {
        Locator brokenEpub = new Locator(Locator.TYPE_EPUB, null, null, null);
        ProgressRequest request = new ProgressRequest("progress-1", "user-001", "book-001", 31L, brokenEpub);

        Set<ConstraintViolation<ProgressRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void aMissingBookIdFailsValidation() {
        ProgressRequest request = new ProgressRequest("progress-1", "user-001", null, 31L, null);

        Set<ConstraintViolation<ProgressRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("bookId is required");
    }
}
