package com.tf.reader.sync.repository;

import com.tf.reader.sync.model.Personalization;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Personalization is user-scoped, so a user has at most one record.
 */
@Repository
public interface PersonalizationRepository extends SyncRepository<Personalization> {

    Optional<Personalization> findFirstByUserIdAndIsDeletedFalse(String userId);
}
