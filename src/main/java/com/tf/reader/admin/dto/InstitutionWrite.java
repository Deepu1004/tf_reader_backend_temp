package com.tf.reader.admin.dto;

import com.tf.reader.catalogue.dto.BrandingView;
import com.tf.reader.catalogue.entity.InstitutionType;
import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What an operator submits to create or update an institution. Code, name, type and country are
 * required; everything else is optional. Codes stay lowercase — institutions are looked up by
 * code on a public page, so changing the casing rules here would break that lookup.
 */
public record InstitutionWrite(
        String code,
        String name,
        InstitutionType type,
        String country,
        String city,
        List<String> emailDomains,
        SignInWrite signIn,
        BrandingView branding) {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]{2,40}$");

    /**
     * Checks every rule and returns a cleaned-up copy, with email domains trimmed, lower-cased,
     * de-duplicated and any blanks removed.
     */
    public InstitutionWrite validate() {
        if (isBlank(code)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "code is required");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED, "code must match ^[a-z0-9-]{2,40}$");
        }
        if (isBlank(name)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "name is required");
        }
        if (type == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "type is required");
        }
        if (isBlank(country)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "country is required");
        }
        if (signIn != null) {
            if (!"SAML".equals(signIn.method())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "signIn.method must be SAML");
            }
            if (signIn.idpHint() != null && signIn.idpHint().length() > SignInWrite.MAX_IDP_HINT_LENGTH) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "signIn.idpHint must be at most " + SignInWrite.MAX_IDP_HINT_LENGTH + " characters");
            }
        }

        return new InstitutionWrite(
                code, name, type, country, city, normaliseEmailDomains(emailDomains), signIn, branding);
    }

    private static List<String> normaliseEmailDomains(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
