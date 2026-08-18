package com.tf.reader.sync.service;

import com.tf.reader.common.error.ApiException;
import com.tf.reader.common.error.ErrorCode;
import com.tf.reader.sync.dto.SyncRequest;
import com.tf.reader.sync.model.BookScopedDocument;
import com.tf.reader.sync.repository.BookScopedRepository;

import java.util.List;

/**
 * Adds book-level filtering for the user + book scoped collections.
 */
public abstract class BookScopedSyncService<T extends BookScopedDocument, R extends SyncRequest<T>>
        extends AbstractSyncService<T, R> {

    private final BookScopedRepository<T> bookScopedRepository;

    protected BookScopedSyncService(BookScopedRepository<T> repository, String entityName) {
        super(repository, entityName);
        this.bookScopedRepository = repository;
    }

    public List<T> findAll(String userId, String bookId, boolean includeDeleted) {
        if (bookId == null || bookId.isBlank()) {
            return findAll(userId, includeDeleted);
        }
        if (userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "userId is required when filtering by bookId");
        }
        return includeDeleted
                ? bookScopedRepository.findByUserIdAndBookId(userId, bookId)
                : bookScopedRepository.findByUserIdAndBookIdAndIsDeletedFalse(userId, bookId);
    }
}
