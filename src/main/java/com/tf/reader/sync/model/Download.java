package com.tf.reader.sync.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Server-side view of a device download.
 *
 * <p>{@code localPath} is deliberately absent: it is device-specific and is never
 * synchronised to MongoDB.
 */
@Document(collection = "downloads")
@CompoundIndex(name = "download_user_book_format_uk",
        def = "{'userId': 1, 'bookId': 1, 'format': 1}", unique = true)
public class Download extends BookScopedDocument {

    private DownloadFormat format;

    private DownloadStatus status;

    /** Download / licence validity. Declared as {@code Boolean} to keep the JSON name "isValid". */
    private Boolean isValid = true;

    private Instant downloadedAt;

    public DownloadFormat getFormat() {
        return format;
    }

    public void setFormat(DownloadFormat format) {
        this.format = format;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public void setStatus(DownloadStatus status) {
        this.status = status;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public Instant getDownloadedAt() {
        return downloadedAt;
    }

    public void setDownloadedAt(Instant downloadedAt) {
        this.downloadedAt = downloadedAt;
    }
}
