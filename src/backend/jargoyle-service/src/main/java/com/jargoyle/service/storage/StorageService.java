package com.jargoyle.service.storage;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.core.io.Resource;

public interface StorageService {
    /**
     * Stores a file and returns the storage key (path relative to the storage root).
     * The implementation decides the directory structure and filename.
     *
     * @throws StorageSaveException if the file cannot be stored (delivered via the future)
     */
    CompletableFuture<String> store(UUID documentId, InputStream content);

    /**
     * Loads a stored file as a Resource.
     *
     * @throws StorageLoadException if the file is not found or cannot be read (delivered via the future)
     */
    CompletableFuture<Resource> load(String storageKey);

    /**
     * Checks whether a stored file exists. Returns {@code false} for null/blank keys
     * or genuinely missing objects.
     *
     * @throws StorageLoadException if the existence check fails due to infrastructure
     *                              errors (delivered via the future)
     */
    CompletableFuture<Boolean> exists(String storageKey);

    /**
     * Deletes a stored file. No-op if the file doesn't exist.
     * Deletion is best-effort — failures are logged but not thrown.
     */
    CompletableFuture<Void> delete(String storageKey);
}
