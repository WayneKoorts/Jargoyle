package com.jargoyle.service.upload;

import java.util.Objects;

import com.jargoyle.dto.DocumentUploadTargetResponse;

/**
 * Pairs a storage key with the upload target details returned to the client.
 *
 * <p>{@code storageKey} is {@code null} for local uploads (where the server
 * determines the key when content arrives) and non-null for S3 presigned URL
 * uploads (where the key is pre-allocated so the document record can reference
 * it before the upload completes).
 */
public record DocumentUploadTargetDescriptor(
    String storageKey,
    DocumentUploadTargetResponse uploadTarget
) {
    public DocumentUploadTargetDescriptor {
        Objects.requireNonNull(uploadTarget, "uploadTarget must not be null");
    }
}
