package com.tf.reader.admin;

import com.tf.reader.admin.dto.InstitutionWrite;
import com.tf.reader.admin.dto.SignInWrite;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No Spring, no Mongo. Every rule {@code InstitutionWrite.validate()} enforces, tested in isolation. */
class InstitutionWriteValidationTest {

    private static InstitutionWrite valid() {
        return new InstitutionWrite(
                "oxford", "University of Oxford", InstitutionType.ACADEMIC, "UK", "Oxford",
                List.of(" OX.AC.UK ", "ox.ac.uk", ""), new SignInWrite("SAML", "oxford-saml-mock"),
                null);
    }

    @Test
    @DisplayName("a fully valid request passes and email domains are normalised")
    void validRequestNormalisesEmailDomains() {
        InstitutionWrite result = valid().validate();

        assertThat(result.emailDomains())
                .as("trimmed, lower-cased, de-duplicated, blanks dropped")
                .containsExactly("ox.ac.uk");
    }

    @ParameterizedTest(name = "code [{0}] is rejected")
    @ValueSource(strings = {"", "OX", "ox ford", "o", "has_underscore", "UPPER"})
    @DisplayName("code must match ^[a-z0-9-]{2,40}$ — institutions stay lowercase, unlike publishers")
    void invalidCodeIsRejected(String badCode) {
        InstitutionWrite request = withCode(badCode);
        assertThatThrownBy(request::validate)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("name, type and country are required")
    void requiredFieldsAreEnforced() {
        assertThatThrownBy(() -> withName(null).validate()).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> withType(null).validate()).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> withCountry("").validate()).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("city, emailDomains, signIn and branding may all be absent")
    void optionalFieldsMayBeNull() {
        InstitutionWrite request =
                new InstitutionWrite(
                        "oxford", "University of Oxford", InstitutionType.ACADEMIC, "UK",
                        null, null, null, null);

        InstitutionWrite result = request.validate();

        assertThat(result.city()).isNull();
        assertThat(result.emailDomains()).isEmpty();
        assertThat(result.signIn()).isNull();
        assertThat(result.branding()).isNull();
    }

    @Test
    @DisplayName("signIn.method must be SAML, and only SAML")
    void signInMethodMustBeSaml() {
        InstitutionWrite request = withSignIn(new SignInWrite("OAUTH", "hint"));
        assertThatThrownBy(request::validate)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SAML");
    }

    @Test
    @DisplayName("idpHint over 60 characters is rejected")
    void idpHintLengthIsEnforced() {
        String tooLong = "x".repeat(SignInWrite.MAX_IDP_HINT_LENGTH + 1);
        InstitutionWrite request = withSignIn(new SignInWrite("SAML", tooLong));
        assertThatThrownBy(request::validate).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("idpHint may be absent even when method is present")
    void idpHintIsOptional() {
        InstitutionWrite request = withSignIn(new SignInWrite("SAML", null));
        assertThat(request.validate().signIn().idpHint()).isNull();
    }

    // ---------------------------------------------------------------------------------- fixtures

    private static InstitutionWrite withCode(String code) {
        InstitutionWrite v = valid();
        return new InstitutionWrite(code, v.name(), v.type(), v.country(), v.city(),
                v.emailDomains(), v.signIn(), v.branding());
    }

    private static InstitutionWrite withName(String name) {
        InstitutionWrite v = valid();
        return new InstitutionWrite(v.code(), name, v.type(), v.country(), v.city(),
                v.emailDomains(), v.signIn(), v.branding());
    }

    private static InstitutionWrite withType(InstitutionType type) {
        InstitutionWrite v = valid();
        return new InstitutionWrite(v.code(), v.name(), type, v.country(), v.city(),
                v.emailDomains(), v.signIn(), v.branding());
    }

    private static InstitutionWrite withCountry(String country) {
        InstitutionWrite v = valid();
        return new InstitutionWrite(v.code(), v.name(), v.type(), country, v.city(),
                v.emailDomains(), v.signIn(), v.branding());
    }

    private static InstitutionWrite withSignIn(SignInWrite signIn) {
        InstitutionWrite v = valid();
        return new InstitutionWrite(v.code(), v.name(), v.type(), v.country(), v.city(),
                v.emailDomains(), signIn, v.branding());
    }
}