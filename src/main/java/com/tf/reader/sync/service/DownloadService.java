package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.DownloadRequest;
import com.tf.reader.sync.model.Download;
import com.tf.reader.sync.repository.DownloadRepository;
import org.springframework.stereotype.Service;

@Service
public class DownloadService extends BookScopedSyncService<Download, DownloadRequest> {

    public DownloadService(DownloadRepository repository) {
        super(repository, "Download");
    }
}
