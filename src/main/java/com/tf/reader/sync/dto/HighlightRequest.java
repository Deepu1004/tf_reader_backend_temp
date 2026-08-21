package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Highlight;
import com.tf.reader.sync.model.Locator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record HighlightRequest(
        String id,

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "bookId is required")
        String bookId,

        @NotNull(message = "startLocator is required")
        @Valid
        Locator startLocator,

        @NotNull(message = "endLocator is required")
        @Valid
        Locator endLocator,

        String color,

        Instant createdAt) implements SyncRequest<Highlight> {

    @Override
    public Highlight toDocument() {
        Highlight document = new Highlight();
        document.setId(id);
        document.setCreatedAt(createdAt);
        applyTo(document);
        return document;
    }

    @Override
    public void applyTo(Highlight target) {
        target.setUserId(userId);
        target.setBookId(bookId);
        target.setStartLocator(startLocator);
        target.setEndLocator(endLocator);
        target.setColor(color);
        // Selected text is intentionally not part of the payload.
    }
}
