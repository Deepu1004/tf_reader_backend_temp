package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Bookmark;
import com.tf.reader.sync.model.Locator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record BookmarkRequest(
        String id,

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "bookId is required")
        String bookId,

        String chapterId,

        @NotNull(message = "locator is required")
        @Valid
        Locator locator,

        String name,

        /** Optional: the device's own creation time. Defaults to server time. */
        Instant createdAt) implements SyncRequest<Bookmark> {

    @Override
    public Bookmark toDocument() {
        Bookmark document = new Bookmark();
        document.setId(id);
        document.setCreatedAt(createdAt);
        applyTo(document);
        return document;
    }

    @Override
    public void applyTo(Bookmark target) {
        target.setUserId(userId);
        target.setBookId(bookId);
        target.setChapterId(chapterId);
        target.setLocator(locator);
        target.setName(name);
        // createdAt is set once, at creation time, and never overwritten by an update.
    }
}
