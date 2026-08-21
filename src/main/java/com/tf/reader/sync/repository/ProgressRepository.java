package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Progress;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgressRepository extends BookScopedRepository<Progress> {
}
