package com.tf.reader.sync.model;

import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Fields shared by every synchronised MongoDB collection.
 *
 * <p>{@code updatedAt} drives Last-Write-Wins conflict resolution and {@code isDeleted}
 * is the tombstone that lets deletes propagate to offline devices. The device-only
 * column {@code synced} is deliberately absent - it is local SQLite state.
 */
public abstract class BaseDocument {

    /** Client-generated UUID. */
    @Id
    private String id;

    private String userId;

    /** LWW timestamp. */
    private Instant updatedAt;

    /** Tombstone. Declared as {@code Boolean} so the JSON property stays "isDeleted". */
    private Boolean isDeleted = false;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}
