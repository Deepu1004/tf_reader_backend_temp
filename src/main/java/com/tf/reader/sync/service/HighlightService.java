package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.HighlightRequest;
import com.tf.reader.sync.model.Highlight;
import com.tf.reader.sync.repository.HighlightRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class HighlightService extends BookScopedSyncService<Highlight, HighlightRequest> {

    public HighlightService(HighlightRepository repository) {
        super(repository, "Highlight");
    }

    @Override
    protected void onCreate(Highlight document, Instant now) {
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
    }
}
