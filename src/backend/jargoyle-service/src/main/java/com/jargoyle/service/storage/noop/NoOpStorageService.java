package com.jargoyle.service.storage.noop;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.jargoyle.service.storage.StorageLoadException;
import com.jargoyle.service.storage.StorageService;

/**
 * Placeholder StorageService that discards all data.
 * Used in production until a proper cloud storage implementation
 * is in place. Allows the app to start and handle non-upload flows.
 */
@Profile("prod")
@Service
public class NoOpStorageService implements StorageService {

    @Override
    public String store(UUID documentId, InputStream content) {
        // Discard the content — return a synthetic key so callers don't break.
        return "noop/%s/%s".formatted(documentId, UUID.randomUUID());
    }

    @Override
    public Resource load(String storageKey) throws StorageLoadException {
        throw new StorageLoadException("Storage is not configured — file uploads are not available in this environment");
    }

    @Override
    public void delete(String storageKey) {
        // Nothing to delete.
    }
}
