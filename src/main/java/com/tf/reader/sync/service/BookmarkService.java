package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.BookmarkRequest;
import com.tf.reader.sync.model.Bookmark;
import com.tf.reader.sync.repository.BookmarkRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BookmarkService extends BookScopedSyncService<Bookmark, BookmarkRequest> {

    public BookmarkService(BookmarkRepository repository) {
        super(repository, "Bookmark");
    }

    @Override
    protected void onCreate(Bookmark document, Instant now) {
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
    }
}
