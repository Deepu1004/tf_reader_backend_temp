package com.tf.reader.sync.dto;

import com.tf.reader.sync.model.BaseDocument;

/**
 * Contract every write payload implements so the generic CRUD service can create
 * and update any collection without knowing its fields.
 */
public interface SyncRequest<T extends BaseDocument> {

    /** Client-supplied UUID; the service generates one when this is null. */
    String id();

    /** Builds a brand new document from this payload. */
    T toDocument();

    /** Copies the mutable fields of this payload onto an existing document. */
    void applyTo(T target);
}
