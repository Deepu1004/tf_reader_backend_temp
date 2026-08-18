package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Accessibility;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Accessibility is user-scoped, so a user has at most one record.
 */
@Repository
public interface AccessibilityRepository extends SyncRepository<Accessibility> {

    Optional<Accessibility> findFirstByUserIdAndIsDeletedFalse(String userId);
}
