package com.tf.reader.sync.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Highlighted range. The selected text is intentionally never stored - only the
 * start and end locators.
 */
@Document(collection = "highlights")
@CompoundIndex(name = "highlight_user_book_idx", def = "{'userId': 1, 'bookId': 1}")
public class Highlight extends BookScopedDocument {

    private Locator startLocator;

    private Locator endLocator;

    private String color;

    private Instant createdAt;

    public Locator getStartLocator() {
        return startLocator;
    }

    public void setStartLocator(Locator startLocator) {
        this.startLocator = startLocator;
    }

    public Locator getEndLocator() {
        return endLocator;
    }

    public void setEndLocator(Locator endLocator) {
        this.endLocator = endLocator;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
