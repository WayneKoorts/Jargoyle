package com.jargoyle.entity;

// Lifecycle: PENDING_UPLOAD -> UPLOADING -> QUEUED -> PROCESSING -> READY
// Any state may also transition to FAILED.
public enum DocumentStatus {
    PENDING_UPLOAD,
    UPLOADING,
    QUEUED,
    PROCESSING,
    READY,
    FAILED
}
