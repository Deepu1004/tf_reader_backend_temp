package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Highlight;
import org.springframework.stereotype.Repository;

@Repository
public interface HighlightRepository extends BookScopedRepository<Highlight> {
}
