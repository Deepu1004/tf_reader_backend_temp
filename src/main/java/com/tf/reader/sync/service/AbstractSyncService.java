package com.tf.reader.sync.service;

import com.tf.reader.sync.dto.SyncRequest;
import com.tf.reader.sync.exception.DuplicateResourceException;
import com.tf.reader.sync.exception.ResourceNotFoundException;
import com.tf.reader.sync.model.BaseDocument;
import com.tf.reader.sync.repository.SyncRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CRUD shared by every synchronised collection.
 *
 * <p>Rules enforced here:
 * <ul>
 *   <li>IDs are client-generated UUIDs; the server only fills in a missing one.</li>
 *   <li>{@code updatedAt} is stamped by the server on every write - it is the LWW clock.</li>
 *   <li>DELETE writes a tombstone by default so the delete can propagate to other devices.</li>
 *   <li>Tombstoned records are invisible to reads unless {@code includeDeleted} is set.</li>
 * </ul>
 */
public abstract class AbstractSyncService<T extends BaseDocument, R extends SyncRequest<T>> {

    protected final SyncRepository<T> repository;
    protected final String entityName;

    protected AbstractSyncService(SyncRepository<T> repository, String entityName) {
        this.repository = repository;
        this.entityName = entityName;
    }

    public T create(R request) {
        T document = request.toDocument();
        if (document.getId() == null || document.getId().isBlank()) {
            document.setId(UUID.randomUUID().toString());
        } else if (repository.existsById(document.getId())) {
            throw new DuplicateResourceException(entityName, document.getId());
        }

        Instant now = Instant.now();
        document.setIsDeleted(false);
        document.setUpdatedAt(now);
        onCreate(document, now);
        return repository.save(document);
    }

    public T findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(entityName, id));
    }

    public List<T> findAll(String userId, boolean includeDeleted) {
        if (userId == null || userId.isBlank()) {
            return includeDeleted ? repository.findAll() : repository.findByIsDeletedFalse();
        }
        return includeDeleted
                ? repository.findByUserId(userId)
                : repository.findByUserIdAndIsDeletedFalse(userId);
    }

    public T update(String id, R request) {
        T document = findActive(id);
        request.applyTo(document);
        document.setId(id);
        document.setUpdatedAt(Instant.now());
        return repository.save(document);
    }

    /** Soft delete: writes the tombstone and bumps the LWW clock. */
    public T delete(String id) {
        T document = findById(id);
        document.setIsDeleted(true);
        document.setUpdatedAt(Instant.now());
        return repository.save(document);
    }

    /** Hard delete: removes the document outright. The delete will not propagate. */
    public void purge(String id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException(entityName, id);
        }
        repository.deleteById(id);
    }

    /** Clears the tombstone so a soft-deleted record can come back. */
    public T restore(String id) {
        T document = findById(id);
        document.setIsDeleted(false);
        document.setUpdatedAt(Instant.now());
        return repository.save(document);
    }

    /** Looks up a record that has not been tombstoned. */
    protected T findActive(String id) {
        T document = findById(id);
        if (Boolean.TRUE.equals(document.getIsDeleted())) {
            throw new ResourceNotFoundException(entityName, id);
        }
        return document;
    }

    /** Hook for per-entity creation defaults, e.g. stamping createdAt. */
    protected void onCreate(T document, Instant now) {
        // no-op by default
    }
}
