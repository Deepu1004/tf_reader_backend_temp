package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Personalization;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalizationRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void omittedTypographyFallsBackToThePointsBasedDefaults() {
        PersonalizationRequest request = new PersonalizationRequest(
                null, "user-001", null, null, null, null, null, null, null, null, null, null, null);

        Personalization document = request.toDocument();

        assertThat(document.getTypographyLineHeight()).isEqualTo(1.5);
        assertThat(document.getTypographyMargins()).isEqualTo(16.0);
        assertThat(document.getUserId()).isEqualTo("user-001");
    }

    @Test
    void aMissingUserIdFailsValidation() {
        PersonalizationRequest request = new PersonalizationRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null);

        Set<ConstraintViolation<PersonalizationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("userId is required");
    }
}
