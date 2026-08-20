package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Accessibility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessibilityRequestTest {

    @Test
    void screenReaderHintsDefaultsToFalseWhenOmitted() {
        AccessibilityRequest request = requestWithScreenReaderHints(null);

        Accessibility document = request.toDocument();

        assertThat(document.isScreenReaderHints()).isFalse();
    }

    @Test
    void screenReaderHintsIsCarriedThroughWhenSetTrue() {
        AccessibilityRequest request = requestWithScreenReaderHints(true);

        Accessibility document = request.toDocument();

        assertThat(document.isScreenReaderHints()).isTrue();
    }

    private static AccessibilityRequest requestWithScreenReaderHints(Boolean screenReaderHints) {
        return new AccessibilityRequest(
                null,            // id
                "user-001",      // userId
                null,            // dyslexiaFont
                null,            // respectOsFontScale
                null,            // boldText
                null,            // reduceMotion
                null,            // ttsEnabled
                null,            // ttsVoiceId
                null,            // ttsRate
                null,            // ttsPitch
                null,            // ttsHighlightMode
                null,            // ttsAutoContinueChapter
                null,            // ttsBackgroundPlayback
                null,            // fontScaleMultiplier
                null,            // readableSpacing
                null,            // highContrast
                null,            // largeTouchTargets
                null,            // largeAudioControls
                null,            // announcePageChanges
                null,            // announceChapterChanges
                screenReaderHints);
    }
}
