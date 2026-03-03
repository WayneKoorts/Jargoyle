package com.jargoyle.service.storage;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.core.io.Resource;

public interface StorageService {
    /**
     * Stores a file and returns the storage key (path relative to the storage root).
     * The implementation decides the directory structure and filename.
     */
    String store(UUID documentId, String originalFilename, InputStream content);

    /**
     * Loads a stored file as a Resource.  Throws if not found.
     */
    Resource load(String storageKey);

    /**
     * Deletes a stored file.  No-op if the file doesn't exist.
     */
    void delete(String storageKey);
}
