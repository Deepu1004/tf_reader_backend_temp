package com.tf.reader.sync.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Accessibility and TTS settings. Scoped to the user only - there is no bookId.
 */
@Document(collection = "accessibility")
@CompoundIndex(name = "accessibility_user_uk", def = "{'userId': 1}", unique = true)
public class Accessibility extends BaseDocument {

    private boolean dyslexiaFont = false;

    private boolean respectOsFontScale = true;

    private boolean boldText = false;

    /** system / on / off */
    private String reduceMotion = "system";

    private boolean ttsEnabled = false;

    private String ttsVoiceId;

    /** 0.5 - 3.0 */
    private Double ttsRate = 1.0;

    private Double ttsPitch = 1.0;

    /** off / sentence / word */
    private String ttsHighlightMode = "sentence";

    private boolean ttsAutoContinueChapter = true;

    private boolean ttsBackgroundPlayback = false;

    private Double fontScaleMultiplier = 1.0;

    private boolean readableSpacing = false;

    private boolean highContrast = false;

    private boolean largeTouchTargets = false;

    private boolean largeAudioControls = false;

    private boolean announcePageChanges = true;

    private boolean announceChapterChanges = true;

    private boolean screenReaderHints = false;

    /** Client-owned. Field name -> ISO-8601 timestamp; the backend never reads this. */
    private Map<String, String> fieldUpdatedAt = Map.of();

    public boolean isDyslexiaFont() {
        return dyslexiaFont;
    }

    public void setDyslexiaFont(boolean dyslexiaFont) {
        this.dyslexiaFont = dyslexiaFont;
    }

    public boolean isRespectOsFontScale() {
        return respectOsFontScale;
    }

    public void setRespectOsFontScale(boolean respectOsFontScale) {
        this.respectOsFontScale = respectOsFontScale;
    }

    public boolean isBoldText() {
        return boldText;
    }

    public void setBoldText(boolean boldText) {
        this.boldText = boldText;
    }

    public String getReduceMotion() {
        return reduceMotion;
    }

    public void setReduceMotion(String reduceMotion) {
        this.reduceMotion = reduceMotion;
    }

    public boolean isTtsEnabled() {
        return ttsEnabled;
    }

    public void setTtsEnabled(boolean ttsEnabled) {
        this.ttsEnabled = ttsEnabled;
    }

    public String getTtsVoiceId() {
        return ttsVoiceId;
    }

    public void setTtsVoiceId(String ttsVoiceId) {
        this.ttsVoiceId = ttsVoiceId;
    }

    public Double getTtsRate() {
        return ttsRate;
    }

    public void setTtsRate(Double ttsRate) {
        this.ttsRate = ttsRate;
    }

    public Double getTtsPitch() {
        return ttsPitch;
    }

    public void setTtsPitch(Double ttsPitch) {
        this.ttsPitch = ttsPitch;
    }

    public String getTtsHighlightMode() {
        return ttsHighlightMode;
    }

    public void setTtsHighlightMode(String ttsHighlightMode) {
        this.ttsHighlightMode = ttsHighlightMode;
    }

    public boolean isTtsAutoContinueChapter() {
        return ttsAutoContinueChapter;
    }

    public void setTtsAutoContinueChapter(boolean ttsAutoContinueChapter) {
        this.ttsAutoContinueChapter = ttsAutoContinueChapter;
    }

    public boolean isTtsBackgroundPlayback() {
        return ttsBackgroundPlayback;
    }

    public void setTtsBackgroundPlayback(boolean ttsBackgroundPlayback) {
        this.ttsBackgroundPlayback = ttsBackgroundPlayback;
    }

    public Double getFontScaleMultiplier() {
        return fontScaleMultiplier;
    }

    public void setFontScaleMultiplier(Double fontScaleMultiplier) {
        this.fontScaleMultiplier = fontScaleMultiplier;
    }

    public boolean isReadableSpacing() {
        return readableSpacing;
    }

    public void setReadableSpacing(boolean readableSpacing) {
        this.readableSpacing = readableSpacing;
    }

    public boolean isHighContrast() {
        return highContrast;
    }

    public void setHighContrast(boolean highContrast) {
        this.highContrast = highContrast;
    }

    public boolean isLargeTouchTargets() {
        return largeTouchTargets;
    }

    public void setLargeTouchTargets(boolean largeTouchTargets) {
        this.largeTouchTargets = largeTouchTargets;
    }

    public boolean isLargeAudioControls() {
        return largeAudioControls;
    }

    public void setLargeAudioControls(boolean largeAudioControls) {
        this.largeAudioControls = largeAudioControls;
    }

    public boolean isAnnouncePageChanges() {
        return announcePageChanges;
    }

    public void setAnnouncePageChanges(boolean announcePageChanges) {
        this.announcePageChanges = announcePageChanges;
    }

    public boolean isAnnounceChapterChanges() {
        return announceChapterChanges;
    }

    public void setAnnounceChapterChanges(boolean announceChapterChanges) {
        this.announceChapterChanges = announceChapterChanges;
    }

    public boolean isScreenReaderHints() {
        return screenReaderHints;
    }

    public void setScreenReaderHints(boolean screenReaderHints) {
        this.screenReaderHints = screenReaderHints;
    }

    public Map<String, String> getFieldUpdatedAt() {
        return fieldUpdatedAt;
    }

    public void setFieldUpdatedAt(Map<String, String> fieldUpdatedAt) {
        this.fieldUpdatedAt = fieldUpdatedAt;
    }
}
