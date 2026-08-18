package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.ProgressRequest;
import com.tf.reader.sync.model.Progress;
import com.tf.reader.sync.repository.ProgressRepository;
import org.springframework.stereotype.Service;

@Service
public class ProgressService extends BookScopedSyncService<Progress, ProgressRequest> {

    public ProgressService(ProgressRepository repository) {
        super(repository, "Progress");
    }
}
