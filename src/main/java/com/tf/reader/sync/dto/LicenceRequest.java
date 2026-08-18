package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Licence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Write payload for a licence. Licences are not part of the offline sync family,
 * so this does not implement {@link SyncRequest}.
 *
 * <p>{@code start} and {@code end} are ISO-8601 instants, e.g.
 * {@code 2026-08-12T00:00:00Z}. A null {@code end} means the licence never expires.
 */
public record LicenceRequest(
        String id,

        @NotBlank(message = "licId is required")
        String licId,

        @NotBlank(message = "bookId is required")
        String bookId,

        @NotNull(message = "start is required")
        Instant start,

        Instant end) {

    public Licence toDocument() {
        Licence document = new Licence();
        document.setId(id);
        document.setLicId(licId);
        document.setBookId(bookId);
        document.setStart(start);
        document.setEnd(end);
        return document;
    }
}
