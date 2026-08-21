package com.tf.reader.sync.model;

/**
 * The schema document does not enumerate download states, so this is the assumed set.
 * Adjust freely - it is stored by name.
 */
public enum DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    EXPIRED
}
