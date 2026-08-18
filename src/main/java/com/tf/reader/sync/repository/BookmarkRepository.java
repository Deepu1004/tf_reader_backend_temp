package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Bookmark;
import org.springframework.stereotype.Repository;

@Repository
public interface BookmarkRepository extends BookScopedRepository<Bookmark> {
}
