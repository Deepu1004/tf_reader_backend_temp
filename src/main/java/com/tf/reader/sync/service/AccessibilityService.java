package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.AccessibilityRequest;
import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.Accessibility;
import com.tf.reader.sync.repository.AccessibilityRepository;
import org.springframework.stereotype.Service;

@Service
public class AccessibilityService extends AbstractSyncService<Accessibility, AccessibilityRequest> {

    private final AccessibilityRepository accessibilityRepository;

    public AccessibilityService(AccessibilityRepository repository) {
        super(repository, "Accessibility");
        this.accessibilityRepository = repository;
    }

    /** Convenience read: a user has at most one accessibility record. */
    public Accessibility findByUserId(String userId) {
        return accessibilityRepository.findFirstByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Accessibility for user", userId));
    }
}
