package com.tf.reader.sync.service;

import com.tf.reader.sync.api.DownloadInvalidation;
import com.tf.reader.sync.dto.DownloadRequest;
import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Download;
import com.tf.reader.sync.repository.DownloadRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DownloadService extends BookScopedSyncService<Download, DownloadRequest>
        implements DownloadInvalidation {

    private final DownloadRepository repository;

    public DownloadService(DownloadRepository repository) {
        super(repository, "Download");
        this.repository = repository;
    }

    /** DownloadInvalidation port — no-op if the user has no download for this title. */
    @Override
    public void invalidate(String userId, String itemId) {
        try {
            updateIsValid(userId, itemId, false);
        } catch (ResourceNotFoundException ignored) {
            // not every loan has a download — absence is not an error
        }
    }

    /** Flips isValid on every download the user holds for the book, e.g. when a licence is revoked. */
    public List<Download> updateIsValid(String userId, String bookId, boolean isValid) {
        List<Download> downloads = repository.findByUserIdAndBookIdAndIsDeletedFalse(userId, bookId);
        if (downloads.isEmpty()) {
            throw new ResourceNotFoundException("Download", "userId=" + userId + ", bookId=" + bookId);
        }

        Instant now = Instant.now();
        downloads.forEach(download -> {
            download.setIsValid(isValid);
            download.setUpdatedAt(now);
        });
        return repository.saveAll(downloads);
    }
}
