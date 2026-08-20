package com.tf.reader.sync.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aPdfLocatorWithAPageIsValid() {
        Locator locator = Locator.pdf(12, 340);

        assertThat(validator.validate(locator)).isEmpty();
        assertThat(locator.getType()).isEqualTo("PDF");
    }

    @Test
    void anEpubLocatorWithoutACfiFailsConsistency() {
        Locator locator = new Locator(Locator.TYPE_EPUB, null, null, null);

        Set<ConstraintViolation<Locator>> violations = validator.validate(locator);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("locator requires 'cfi' when type is 'epub' and 'page' when type is 'pdf'");
    }

    @Test
    void lowercaseTypeIsRejectedNowThatTheContractIsUppercase() {
        Locator locator = new Locator("pdf", null, 1, null);

        Set<ConstraintViolation<Locator>> violations = validator.validate(locator);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("locator.type must be 'PDF' or 'EPUB'");
    }
}
