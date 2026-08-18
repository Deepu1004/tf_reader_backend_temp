package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Accessibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.util.Objects;

/**
 * User-scoped accessibility settings. There is no bookId by design.
 * Omitted fields fall back to the schema defaults.
 */
public record AccessibilityRequest(
        String id,

        @NotBlank(message = "userId is required")
        String userId,

        Boolean dyslexiaFont,

        Boolean respectOsFontScale,

        Boolean boldText,

        @Pattern(regexp = "system|on|off", message = "reduceMotion must be 'system', 'on' or 'off'")
        String reduceMotion,

        Boolean ttsEnabled,

        String ttsVoiceId,

        @DecimalMin(value = "0.5", message = "ttsRate must be between 0.5 and 3.0")
        @DecimalMax(value = "3.0", message = "ttsRate must be between 0.5 and 3.0")
        Double ttsRate,

        @Positive(message = "ttsPitch must be greater than zero")
        Double ttsPitch,

        @Pattern(regexp = "off|sentence|word", message = "ttsHighlightMode must be 'off', 'sentence' or 'word'")
        String ttsHighlightMode,

        Boolean ttsAutoContinueChapter,

        Boolean ttsBackgroundPlayback,

        @Positive(message = "fontScaleMultiplier must be greater than zero")
        Double fontScaleMultiplier,

        Boolean readableSpacing,

        Boolean highContrast,

        Boolean largeTouchTargets,

        Boolean largeAudioControls,

        Boolean announcePageChanges,

        Boolean announceChapterChanges,

        Boolean screenReaderHints) implements SyncRequest<Accessibility> {

    @Override
    public Accessibility toDocument() {
        Accessibility document = new Accessibility();
        document.setId(id);
        applyTo(document);
        return document;
    }

    @Override
    public void applyTo(Accessibility target) {
        target.setUserId(userId);
        target.setDyslexiaFont(Objects.requireNonNullElse(dyslexiaFont, false));
        target.setRespectOsFontScale(Objects.requireNonNullElse(respectOsFontScale, true));
        target.setBoldText(Objects.requireNonNullElse(boldText, false));
        target.setReduceMotion(Objects.requireNonNullElse(reduceMotion, "system"));
        target.setTtsEnabled(Objects.requireNonNullElse(ttsEnabled, false));
        target.setTtsVoiceId(ttsVoiceId);
        target.setTtsRate(Objects.requireNonNullElse(ttsRate, 1.0));
        target.setTtsPitch(Objects.requireNonNullElse(ttsPitch, 1.0));
        target.setTtsHighlightMode(Objects.requireNonNullElse(ttsHighlightMode, "sentence"));
        target.setTtsAutoContinueChapter(Objects.requireNonNullElse(ttsAutoContinueChapter, true));
        target.setTtsBackgroundPlayback(Objects.requireNonNullElse(ttsBackgroundPlayback, false));
        target.setFontScaleMultiplier(Objects.requireNonNullElse(fontScaleMultiplier, 1.0));
        target.setReadableSpacing(Objects.requireNonNullElse(readableSpacing, false));
        target.setHighContrast(Objects.requireNonNullElse(highContrast, false));
        target.setLargeTouchTargets(Objects.requireNonNullElse(largeTouchTargets, false));
        target.setLargeAudioControls(Objects.requireNonNullElse(largeAudioControls, false));
        target.setAnnouncePageChanges(Objects.requireNonNullElse(announcePageChanges, true));
        target.setAnnounceChapterChanges(Objects.requireNonNullElse(announceChapterChanges, true));
        target.setScreenReaderHints(Objects.requireNonNullElse(screenReaderHints, false));
    }
}
