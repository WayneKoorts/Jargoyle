package com.jargoyle.service.storage.local;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

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

        var storageKey = storageService.store(documentId, contentBytes);

        assertThat(storageKey).startsWith(documentId.toString());

        var filePath = tempDir.resolve(storageKey);
        assertThat(filePath).exists();
        assertThat(Files.readString(filePath)).isEqualTo(testFileContents);
    }

}
