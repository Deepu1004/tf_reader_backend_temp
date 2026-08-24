package com.tf.reader.sync.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Named bookmark positioned by chapter + structured locator.
 */
@Document(collection = "bookmarks")
@CompoundIndex(name = "bookmark_user_book_idx", def = "{'userId': 1, 'bookId': 1}")
@CompoundIndex(name = "bookmark_locator_uk",
        def = "{'userId': 1, 'bookId': 1, 'locator': 1}",
        unique = true,
        partialFilter = "{'isDeleted': false}")
public class Bookmark extends BookScopedDocument {

    private String chapterId;

    private Locator locator;

    private String name;

    private Instant createdAt;

    public String getChapterId() {
        return chapterId;
    }

    public void setChapterId(String chapterId) {
        this.chapterId = chapterId;
    }

    public Locator getLocator() {
        return locator;
    }

    public void setLocator(Locator locator) {
        this.locator = locator;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
