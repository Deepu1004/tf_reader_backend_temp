package com.tf.reader.sync.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Reading position for a user + book. One record per (userId, bookId).
 */
@Document(collection = "progress")
@CompoundIndex(name = "progress_user_book_uk", def = "{'userId': 1, 'bookId': 1}", unique = true)
public class Progress extends BookScopedDocument {

    /** Current reading position, expressed as an offset. Authoritative for PDF. */
    private Long offset;

    /** Structured position. Nullable - rows written before this field existed have none. */
    private Locator locator;

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public Locator getLocator() {
        return locator;
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }
}
