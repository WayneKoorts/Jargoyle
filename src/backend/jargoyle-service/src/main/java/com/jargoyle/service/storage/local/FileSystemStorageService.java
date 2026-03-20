package com.jargoyle.service.storage.local;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FileSystemStorageService.class);

    private final String storageRoot;

    public FileSystemStorageService(
        @Value("${jargoyle.storage.local.root-dir:./data/uploads}") String storageRoot
    ) {
        this.storageRoot = storageRoot;
    }

    @Override
    public CompletableFuture<String> store(UUID documentId, InputStream content) {
        return CompletableFuture.supplyAsync(() -> {
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
        });
    }

    @Override
    public CompletableFuture<Resource> load(String storageKey) {
        return CompletableFuture.supplyAsync(() -> {
            if (storageKey == null || storageKey.isBlank()) {
                throw new IllegalArgumentException("storageKey cannot be empty");
            }

            var filePath = Path.of(storageRoot, storageKey);
            if (!Files.exists(filePath)) {
                throw new StorageLoadException("File not found");
            }

            try {
                return new UrlResource(filePath.toUri());
            } catch (MalformedURLException ex) {
                throw new StorageLoadException("File not found", ex);
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String storageKey) {
        return CompletableFuture.supplyAsync(() -> {
            if (storageKey == null || storageKey.isBlank()) {
                return false;
            }

            return Files.exists(Path.of(storageRoot, storageKey));
        });
    }

    @Override
    public CompletableFuture<Void> delete(String storageKey) {
        return CompletableFuture.runAsync(() -> {
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
                log.warn("Best-effort delete failed for {}: {}", filePath, e.getMessage(), e);
            }
        });
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findFirst().isEmpty();
        }
    }

}
