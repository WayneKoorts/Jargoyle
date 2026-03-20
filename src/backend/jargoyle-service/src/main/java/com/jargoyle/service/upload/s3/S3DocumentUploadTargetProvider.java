package com.jargoyle.service.upload.s3;

import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.jargoyle.dto.DocumentUploadTargetResponse;
import com.jargoyle.service.properties.S3StorageProperties;
import com.jargoyle.service.storage.s3.S3StorageService;
import com.jargoyle.service.upload.DocumentUploadTargetDescriptor;
import com.jargoyle.service.upload.DocumentUploadTargetProvider;

import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@Profile("prod")
public class S3DocumentUploadTargetProvider implements DocumentUploadTargetProvider {

    private final S3Presigner s3Presigner;
    private final S3StorageProperties properties;

    public S3DocumentUploadTargetProvider(S3Presigner s3Presigner, S3StorageProperties properties) {
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    @Override
    public DocumentUploadTargetDescriptor createUploadTarget(UUID documentId, String originalFilename) {
        var storageKey = S3StorageService.generateStorageKey(documentId);
        // Don't set contentType here — it becomes a signed header, meaning
        // the client must send the exact same Content-Type or S3 returns 403
        // SignatureDoesNotMatch. Content validation happens in finaliseUpload.
        var putRequest = PutObjectRequest.builder()
                .bucket(properties.bucketName())
                .key(storageKey)
                .build();

        Duration signatureDuration = properties.uploadUrlTtl();
        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(putRequest)
                .build();

        var presignedPutRequest = s3Presigner.presignPutObject(presignRequest);

        return new DocumentUploadTargetDescriptor(
                storageKey,
                new DocumentUploadTargetResponse(presignedPutRequest.url().toString(), "PUT")
        );
    }
}