package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Personalization;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Map;
import java.util.Objects;

/**
 * Omitted fields fall back to the schema defaults, so a PUT always leaves the
 * document in a fully-populated, predictable state.
 */
public record PersonalizationRequest(
        String id,

        @NotBlank(message = "userId is required")
        String userId,

        String theme,

        String fontFamily,

        String customFontUri,

        @Positive(message = "typographySize must be greater than zero")
        Double typographySize,

        @Positive(message = "typographyLineHeight must be greater than zero")
        Double typographyLineHeight,

        @PositiveOrZero(message = "typographySpacing must be zero or greater")
        Double typographySpacing,

        @PositiveOrZero(message = "typographyMargins must be zero or greater")
        Double typographyMargins,

        String layoutFlow,

        String layoutSpread,

        @Positive(message = "zoom must be greater than zero")
        Double zoom,

        /** Client-owned. Field name -> ISO-8601 timestamp, carried through as-is. */
        Map<String, String> fieldUpdatedAt) implements SyncRequest<Personalization> {

    @Override
    public Personalization toDocument() {
        Personalization document = new Personalization();
        document.setId(id);
        applyTo(document);
        return document;
    }

    @Override
    public void applyTo(Personalization target) {
        target.setUserId(userId);
        target.setTheme(Objects.requireNonNullElse(theme, "system"));
        target.setFontFamily(Objects.requireNonNullElse(fontFamily, "system"));
        target.setCustomFontUri(customFontUri);
        target.setTypographySize(Objects.requireNonNullElse(typographySize, 1.0));
        target.setTypographyLineHeight(Objects.requireNonNullElse(typographyLineHeight, 1.5));
        target.setTypographySpacing(Objects.requireNonNullElse(typographySpacing, 0.0));
        target.setTypographyMargins(Objects.requireNonNullElse(typographyMargins, 16.0));
        target.setLayoutFlow(Objects.requireNonNullElse(layoutFlow, "paginated"));
        target.setLayoutSpread(Objects.requireNonNullElse(layoutSpread, "single"));
        target.setZoom(Objects.requireNonNullElse(zoom, 1.0));
        target.setFieldUpdatedAt(Objects.requireNonNullElse(fieldUpdatedAt, Map.of()));
    }
}
