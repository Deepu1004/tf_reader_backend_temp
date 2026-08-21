package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Locator;
import com.tf.reader.sync.model.Progress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProgressRequest(
        String id,

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "bookId is required")
        String bookId,

        @NotNull(message = "offset is required")
        @PositiveOrZero(message = "offset must be zero or greater")
        Long offset,

        /** Nullable - rows written before this field existed send null. */
        @Valid
        Locator locator) implements SyncRequest<Progress> {

    @Override
    public Progress toDocument() {
        Progress document = new Progress();
        document.setId(id);
        applyTo(document);
        return document;
    }

    @Override
    public void applyTo(Progress target) {
        target.setUserId(userId);
        target.setBookId(bookId);
        target.setOffset(offset);
        target.setLocator(locator);
    }
}
