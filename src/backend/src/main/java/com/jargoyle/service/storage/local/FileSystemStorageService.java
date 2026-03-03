package com.jargoyle.service.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import com.jargoyle.service.storage.StorageLoadException;
import com.jargoyle.service.storage.StorageSaveException;
import com.jargoyle.service.storage.StorageService;

@Profile("dev")
@Service
public class FileSystemStorageService implements StorageService {

    private final String storageRoot;

    public FileSystemStorageService(
        @Value("${jargoyle.storage.local.root-dir:./data/uploads}") String storageRoot
    ) {
        this.storageRoot = storageRoot;
    }

    @Override
    public String store(UUID documentId, InputStream content) throws StorageSaveException {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }

        if (!Files.exists(Path.of(storageRoot))) {
            throw new StorageSaveException("Storage root " + storageRoot + " doesn't exist");
        }

        var storageKey = String.format("%s/%s", documentId.toString(), UUID.randomUUID().toString());
        var destinationPath = Path.of(storageRoot, storageKey);
        try {
            Files.createDirectories(destinationPath.getParent());
            Files.write(destinationPath, content.readAllBytes());
        } catch (IOException ex) {
            throw new StorageSaveException("Failed to store file", ex);
        }
        
        return storageKey;
    }

    @Override
    public Resource load(String storageKey) throws StorageLoadException {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey cannot be empty");
        }

        var filePath = Path.of(storageRoot, storageKey);
        if (!Files.exists(filePath)) {
            throw new StorageLoadException("File not found");
        }

        Resource loadedFile;
        try {
            loadedFile = new UrlResource(filePath.toUri());
        } catch (MalformedURLException ex) {
            throw new StorageLoadException("File not found", ex);
        }

        return loadedFile;
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        var filePath = Path.of(storageRoot, storageKey);
        try {
            Files.deleteIfExists(filePath);

            // Remove the parent directory (the documentId folder) if it's now empty
            var parentDir = filePath.getParent();
            if (parentDir != null && Files.isDirectory(parentDir) && isEmptyDirectory(parentDir)) {
                Files.delete(parentDir);
            }
        } catch (IOException e) {
            // TODO: Log but don't throw — delete is best-effort per the interface contract
        }
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findFirst().isEmpty();
        }
    }

}
