package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.BookScopedDocument;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Adds book-level filtering for the user + book scoped collections.
 */
@NoRepositoryBean
public interface BookScopedRepository<T extends BookScopedDocument> extends SyncRepository<T> {

    List<T> findByUserIdAndBookId(String userId, String bookId);

    List<T> findByUserIdAndBookIdAndIsDeletedFalse(String userId, String bookId);
}
