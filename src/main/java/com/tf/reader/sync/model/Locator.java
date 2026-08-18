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
 * EPUB: {"type":"EPUB","cfi":"epubcfi(...)"}
 * PDF : {"type":"PDF","page":12,"offset":340}
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Locator {

    public static final String TYPE_EPUB = "EPUB";
    public static final String TYPE_PDF = "PDF";

    @NotBlank(message = "locator.type is required")
    @Pattern(regexp = TYPE_EPUB + "|" + TYPE_PDF, message = "locator.type must be 'PDF' or 'EPUB'")
    private String type;

    /** EPUB only. */
    private String cfi;

    /** PDF only. */
    @PositiveOrZero(message = "locator.page must be zero or greater")
    private Integer page;

    /** PDF only, optional character offset within the page. */
    @PositiveOrZero(message = "locator.offset must be zero or greater")
    private Integer offset;

    public Locator() {
    }

    public Locator(String type, String cfi, Integer page, Integer offset) {
        this.type = type;
        this.cfi = cfi;
        this.page = page;
        this.offset = offset;
    }

    public static Locator epub(String cfi) {
        return new Locator(TYPE_EPUB, cfi, null, null);
    }

    public static Locator pdf(Integer page, Integer offset) {
        return new Locator(TYPE_PDF, null, page, offset);
    }

    @JsonIgnore
    @AssertTrue(message = "locator requires 'cfi' when type is 'epub' and 'page' when type is 'pdf'")
    public boolean isConsistent() {
        if (TYPE_EPUB.equals(type)) {
            return cfi != null && !cfi.isBlank();
        }
        if (TYPE_PDF.equals(type)) {
            return page != null;
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
}
