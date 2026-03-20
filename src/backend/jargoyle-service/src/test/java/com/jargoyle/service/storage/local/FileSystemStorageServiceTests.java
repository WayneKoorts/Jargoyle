package com.jargoyle.service.storage.local;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.jargoyle.service.storage.StorageLoadException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FileSystemStorageServiceTests {

    @TempDir
    Path tempDir;

    FileSystemStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new FileSystemStorageService(tempDir.toString());
    }

    @Test
    void store_writesFileAndReturnsStorageKey() throws Exception {
        var testFileContents = "hello";
        var contentBytes = new ByteArrayInputStream(testFileContents.getBytes());
        var documentId = UUID.randomUUID();

        var storageKey = storageService.store(documentId, contentBytes).join();

        assertThat(storageKey).startsWith(documentId.toString());

        var filePath = tempDir.resolve(storageKey);
        assertThat(filePath).exists();
        assertThat(Files.readString(filePath)).isEqualTo(testFileContents);
    }

    @Test
    void exists_storedFile_returnsTrue() {
        var documentId = UUID.randomUUID();
        var content = new ByteArrayInputStream("data".getBytes());
        var storageKey = storageService.store(documentId, content).join();

        var exists = storageService.exists(storageKey).join();

        assertThat(exists).isTrue();
    }

    @Test
    void exists_missingFile_returnsFalse() {
        var exists = storageService.exists("nonexistent/file.pdf").join();

        assertThat(exists).isFalse();
    }

    @Test
    void exists_nullKey_returnsFalse() {
        var exists = storageService.exists(null).join();

        assertThat(exists).isFalse();
    }

    @Test
    void exists_blankKey_returnsFalse() {
        var exists = storageService.exists("  ").join();

        assertThat(exists).isFalse();
    }

    @Test
    void load_storedFile_returnsResource() throws Exception {
        var documentId = UUID.randomUUID();
        var content = new ByteArrayInputStream("file content".getBytes());
        var storageKey = storageService.store(documentId, content).join();

        var resource = storageService.load(storageKey).join();

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        // Close the stream explicitly so the file handle is released before
        // JUnit's @TempDir cleanup runs (required on Windows).
        try (var inputStream = resource.getInputStream()) {
            var loadedContent = new String(inputStream.readAllBytes());
            assertThat(loadedContent).isEqualTo("file content");
        }
    }

    @Test
    void load_missingFile_throwsStorageLoadException() {
        assertThatThrownBy(() -> storageService.load("nonexistent/file.pdf").join())
                .hasCauseInstanceOf(StorageLoadException.class);
    }

    @Test
    void load_nullKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> storageService.load(null).join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_storedFile_removesFile() {
        var documentId = UUID.randomUUID();
        var content = new ByteArrayInputStream("data".getBytes());
        var storageKey = storageService.store(documentId, content).join();

        storageService.delete(storageKey).join();

        var exists = storageService.exists(storageKey).join();
        assertThat(exists).isFalse();
    }

    @Test
    void delete_missingFile_doesNotThrow() {
        // Should be a no-op, not throw
        storageService.delete("nonexistent/file.pdf").join();
    }

    @Test
    void delete_nullKey_doesNotThrow() {
        storageService.delete(null).join();
    }
}
