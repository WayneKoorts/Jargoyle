package com.jargoyle.service.upload.local;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.jargoyle.dto.DocumentUploadTargetResponse;
import com.jargoyle.service.upload.DocumentUploadTargetDescriptor;
import com.jargoyle.service.upload.DocumentUploadTargetProvider;

@Service
@Profile("!prod")
public class LocalDocumentUploadTargetProvider implements DocumentUploadTargetProvider {

    @Override
    public DocumentUploadTargetDescriptor createUploadTarget(UUID documentId, String originalFilename) {
        return new DocumentUploadTargetDescriptor(
                null,
                new DocumentUploadTargetResponse("/documents/%s/content".formatted(documentId), "PUT")
        );
    }
}