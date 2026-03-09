package com.jargoyle.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.core.io.Resource;

public interface StorageService {
    /**
     * Stores a file and returns the storage key (path relative to the storage root).
     * The implementation decides the directory structure and filename.
     * @throws IOException 
     */
    String store(UUID documentId, InputStream content) throws StorageSaveException;

    /**
     * Loads a stored file as a Resource.  Throws if not found.
     * @throws StorageLoadException 
     */
    Resource load(String storageKey) throws StorageLoadException;

    /**
     * Deletes a stored file.  No-op if the file doesn't exist.
     */
    void delete(String storageKey);
}
