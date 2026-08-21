package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Download;
import org.springframework.stereotype.Repository;

@Repository
public interface DownloadRepository extends BookScopedRepository<Download> {
}
