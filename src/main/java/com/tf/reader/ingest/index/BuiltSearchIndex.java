package com.tf.reader.ingest.index;

/**
 * The result wokay's ingestion needs: the serialized search index and its distinct-term count (for
 * the content grant's {@code IndexUrl.termCount}). {@code json} is plaintext — encryption, storage and
 * bundling are wokay's, and to them the bytes are an opaque blob.
 */
public record BuiltSearchIndex(byte[] json, int termCount) {
}
