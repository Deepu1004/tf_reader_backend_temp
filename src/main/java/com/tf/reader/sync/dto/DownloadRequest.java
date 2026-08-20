package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.Download;
import com.tf.reader.sync.model.DownloadFormat;
import com.tf.reader.sync.model.DownloadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Note the absence of localPath: it is device-specific and never leaves the device.
 */
public record DownloadRequest(
        String id,

        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "bookId is required")
        String bookId,

        @NotNull(message = "format is required (EPUB, PDF or AUDIO)")
        DownloadFormat format,

        DownloadStatus status,

        Boolean isValid,

        Instant downloadedAt) implements SyncRequest<Download> {

    @Override
    public Download toDocument() {
        Download document = new Download();
        document.setId(id);
        applyTo(document);
        return document;
    }

    @Override
    public void applyTo(Download target) {
        target.setUserId(userId);
        target.setBookId(bookId);
        target.setFormat(format);
        target.setStatus(Objects.requireNonNullElse(status, DownloadStatus.QUEUED));
        target.setIsValid(Objects.requireNonNullElse(isValid, true));
        target.setDownloadedAt(downloadedAt);
    }
}
