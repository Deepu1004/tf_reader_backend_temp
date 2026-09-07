package com.tf.reader.sync.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.AssertTrue;

/**
 * Structured reading position, stored as a nested object (never a bare string).
 *
 * <pre>
 * EPUB : {"type":"EPUB","cfi":"epubcfi(...)"}
 * PDF  : {"type":"PDF","page":12,"offset":340}
 * AUDIO: {"type":"AUDIO","positionMs":45000}
 * AUDIO: {"type":"AUDIO","positionMs":45000,"trackId":"track-1"}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Locator {

    public static final String TYPE_EPUB = "EPUB";
    public static final String TYPE_PDF = "PDF";
    public static final String TYPE_AUDIO = "AUDIO";

    @NotBlank(message = "locator.type is required")
    @Pattern(regexp = TYPE_EPUB + "|" + TYPE_PDF + "|" + TYPE_AUDIO, message = "locator.type must be 'EPUB', 'PDF' or 'AUDIO'")
    private String type;

    /** EPUB only. */
    private String cfi;

    /** PDF only. */
    @PositiveOrZero(message = "locator.page must be zero or greater")
    private Integer page;

    /** PDF only, optional character offset within the page. */
    @PositiveOrZero(message = "locator.offset must be zero or greater")
    private Integer offset;

    /** AUDIO only. Milliseconds from the start of the track. */
    @PositiveOrZero(message = "locator.positionMs must be zero or greater")
    private Long positionMs;

    /** AUDIO only, optional. Omitted for single-track audio. */
    private String trackId;

    public Locator() {
    }

    public Locator(String type, String cfi, Integer page, Integer offset, Long positionMs, String trackId) {
        this.type = type;
        this.cfi = cfi;
        this.page = page;
        this.offset = offset;
        this.positionMs = positionMs;
        this.trackId = trackId;
    }

    public static Locator epub(String cfi) {
        return new Locator(TYPE_EPUB, cfi, null, null, null, null);
    }

    public static Locator pdf(Integer page, Integer offset) {
        return new Locator(TYPE_PDF, null, page, offset, null, null);
    }

    public static Locator audio(Long positionMs, String trackId) {
        return new Locator(TYPE_AUDIO, null, null, null, positionMs, trackId);
    }

    @JsonIgnore
    @AssertTrue(message = "locator requires 'cfi' when type is 'EPUB', 'page' when type is 'PDF', and 'positionMs' when type is 'AUDIO'")
    public boolean isConsistent() {
        if (TYPE_EPUB.equals(type)) {
            return cfi != null && !cfi.isBlank();
        }
        if (TYPE_PDF.equals(type)) {
            return page != null;
        }
        if (TYPE_AUDIO.equals(type)) {
            return positionMs != null;
        }
        // Unknown types are already rejected by @Pattern; no need to fail twice.
        return true;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCfi() {
        return cfi;
    }

    public void setCfi(String cfi) {
        this.cfi = cfi;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    public Long getPositionMs() {
        return positionMs;
    }

    public void setPositionMs(Long positionMs) {
        this.positionMs = positionMs;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }
}
