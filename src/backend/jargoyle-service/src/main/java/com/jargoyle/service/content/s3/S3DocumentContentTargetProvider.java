package com.jargoyle.service.content.s3;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;

import com.jargoyle.service.content.DocumentContentTargetProvider;
import com.jargoyle.service.properties.S3StorageProperties;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Generates presigned S3 GET URLs for direct browser access to original document content.
 *
 * <p>Active in the {@code prod} profile. The presigned URL allows the browser to fetch
 * the file directly from S3, bypassing the application server. The URL includes a
 * {@code Content-Disposition: inline} override so the browser renders the content
 * rather than downloading it.</p>
 *
 * <p>Reuses the {@link S3StorageProperties#uploadUrlTtl()} for the signature duration.
 * A separate download TTL can be added later if different expiry behaviour is needed.</p>
 */
@Service
@Profile("prod")
public class S3DocumentContentTargetProvider implements DocumentContentTargetProvider {

    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;

    /**
     * Creates a new provider with the given S3 presigner and storage properties.
     *
     * @param s3Presigner the AWS S3 presigner bean
     * @param properties  the S3 storage configuration properties
     */
    public S3DocumentContentTargetProvider(S3Presigner s3Presigner, S3StorageProperties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public String createContentUrl(UUID documentId, String storageKey, String originalFilename) {
        var getRequestBuilder = GetObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(storageKey);

        // Override the response Content-Disposition so the browser renders inline
        // rather than prompting a download. Uses Spring's ContentDisposition builder
        // to safely escape filenames containing quotes, semicolons, or other special
        // characters that could produce a malformed header value.
        var contentDisposition = originalFilename != null && !originalFilename.isBlank()
                ? ContentDisposition.inline().filename(originalFilename).build()
                : ContentDisposition.inline().build();
        getRequestBuilder.responseContentDisposition(contentDisposition.toString());

        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.uploadUrlTtl())
                .getObjectRequest(getRequestBuilder.build())
                .build();

        var presignedGetRequest = s3Presigner.presignGetObject(presignRequest);

        return presignedGetRequest.url().toString();
    }
}
