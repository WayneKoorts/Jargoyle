package com.jargoyle.service.storage.s3;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.jargoyle.service.properties.S3StorageProperties;
import com.jargoyle.service.storage.StorageLoadException;
import com.jargoyle.service.storage.StorageSaveException;
import com.jargoyle.service.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * AWS S3 implementation of {@link StorageService}. Uses the non-blocking
 * {@link S3AsyncClient} so every operation returns a {@link CompletableFuture}
 * natively — no thread-pool wrapping needed.
 *
 * <p>Storage keys follow the same {@code {documentId}/{randomUUID}} pattern as
 * the local filesystem implementation, used as the S3 object key within the
 * configured bucket.
 */
@Service
@Profile("prod")
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3AsyncClient s3AsyncClient;
    private final S3StorageProperties properties;

    public S3StorageService(S3AsyncClient s3AsyncClient, S3StorageProperties properties) {
        this.s3AsyncClient = s3AsyncClient;
        this.properties = properties;
    }

    @Override
    public CompletableFuture<String> store(UUID documentId, InputStream content) {
        if (content == null) {
            throw new IllegalArgumentException("content cannot be null");
        }

        var storageKey = generateStorageKey(documentId);

        // S3 PutObject requires the content length up front, so we read
        // the stream into a byte array. This is safe given the 10 MB upload cap.
        byte[] bytes;
        try {
            bytes = content.readAllBytes();
        } catch (IOException ex) {
            // Wrap in a failed future so all errors are delivered consistently
            // via the CompletableFuture, not thrown synchronously.
            return CompletableFuture.failedFuture(
                    new StorageSaveException("Failed to read upload content", ex));
        }

        var putRequest = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(storageKey)
                .contentLength((long) bytes.length)
                .build();

        return s3AsyncClient.putObject(putRequest, AsyncRequestBody.fromBytes(bytes))
                .thenApply(response -> storageKey)
                .exceptionally(ex -> {
                    throw new StorageSaveException("Failed to store file in S3", ex);
                });
    }

    @Override
    public CompletableFuture<Resource> load(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey cannot be empty");
        }

        var getRequest = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(storageKey)
                .build();

        return s3AsyncClient.getObject(getRequest, AsyncResponseTransformer.toBytes())
                .thenApply(responseBytes -> (Resource) new InputStreamResource(responseBytes.asInputStream()))
                .exceptionally(ex -> {
                    throw new StorageLoadException("Failed to load file from S3: " + storageKey, ex);
                });
    }

    @Override
    public CompletableFuture<Boolean> exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        var headRequest = HeadObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(storageKey)
                .build();

        return s3AsyncClient.headObject(headRequest)
                .thenApply(response -> true)
                .exceptionally(ex -> {
                    // Unwrap CompletionException to get the real cause
                    var cause = ex instanceof java.util.concurrent.CompletionException ? ex.getCause() : ex;
                    // A 404 (NoSuchKeyException or S3Exception with status 404) means the object
                    // genuinely doesn't exist — return false. Any other error (network timeout,
                    // auth failure, S3 outage) is a real infrastructure problem that must propagate.
                    if (cause instanceof NoSuchKeyException
                            || (cause instanceof S3Exception s3ex && s3ex.statusCode() == 404)) {
                        return false;
                    }
                    throw new StorageLoadException("Failed to check existence in S3: " + storageKey, cause);
                });
    }

    @Override
    public CompletableFuture<Void> delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        var deleteRequest = DeleteObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(storageKey)
                .build();

        // S3 deleteObject is already a no-op for missing keys, matching the interface contract.
        // Log but don't throw on failure — delete is best-effort.
        return s3AsyncClient.deleteObject(deleteRequest)
                .thenApply(response -> (Void) null)
                .exceptionally(ex -> {
                    log.warn("Failed to delete S3 object {}: {}", storageKey, ex.getMessage(), ex);
                    return null;
                });
    }

    /**
     * Generates a storage key following the {@code {documentId}/{randomUUID}} pattern.
     * Shared with {@link com.jargoyle.service.upload.s3.S3DocumentUploadTargetProvider}
     * which pre-allocates the key for presigned URLs.
     */
    public static String generateStorageKey(UUID documentId) {
        return "%s/%s".formatted(documentId, UUID.randomUUID());
    }
}
