package com.jargoyle.service.content;

import java.util.UUID;

/**
 * Strategy interface for generating URLs to access original document content.
 *
 * <p>Mirrors the upload-side {@link com.jargoyle.service.upload.DocumentUploadTargetProvider}
 * pattern. Implementations are selected by Spring profile: production generates
 * presigned S3 GET URLs for direct browser access; local development returns a
 * backend-relative URL that serves the file through the application.</p>
 */
public interface DocumentContentTargetProvider {

    /**
     * Creates a URL from which the browser can fetch the original document content.
     *
     * @param documentId       the ID of the document
     * @param storageKey       the storage key referencing the stored file
     * @param originalFilename the user-supplied filename, may be {@code null}
     * @return a URL string the frontend can use to load the document content
     */
    String createContentUrl(UUID documentId, String storageKey, String originalFilename);
}
