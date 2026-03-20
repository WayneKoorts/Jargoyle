package com.jargoyle.service.upload;

import java.util.UUID;

/**
 * Strategy interface for generating upload targets. Implementations are
 * selected by Spring profile: local dev returns a relative URL for direct
 * multipart upload; production returns an S3 presigned URL.
 */
public interface DocumentUploadTargetProvider {

    /**
     * Creates an upload target for the given document.
     *
     * @param documentId       the ID of the document being uploaded
     * @param originalFilename the user-supplied filename, may be {@code null}
     * @return a descriptor containing the upload target URL and optional pre-allocated storage key
     */
    DocumentUploadTargetDescriptor createUploadTarget(UUID documentId, String originalFilename);
}
