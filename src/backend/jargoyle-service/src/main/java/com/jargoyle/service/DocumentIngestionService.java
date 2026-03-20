package com.jargoyle.service;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentUploadSessionRequest;
import com.jargoyle.dto.DocumentUploadSessionResponse;
import com.jargoyle.dto.DocumentUploadTargetResponse;
import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.InputType;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.exception.DocumentNotReadyException;
import com.jargoyle.service.storage.StorageSaveException;
import com.jargoyle.service.storage.StorageService;
import com.jargoyle.service.upload.DocumentUploadTargetDescriptor;
import com.jargoyle.service.upload.DocumentUploadTargetProvider;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final DocumentProcessingDispatcher documentProcessingDispatcher;
    private final DocumentStatusNotifier documentStatusNotifier;
    private final StorageService storageService;
    private final DocumentUploadTargetProvider documentUploadTargetProvider;

    public DocumentIngestionService(
            UserRepository userRepository,
            DocumentRepository documentRepository,
            DocumentService documentService,
            DocumentProcessingDispatcher documentProcessingDispatcher,
            DocumentStatusNotifier documentStatusNotifier,
            StorageService storageService,
            DocumentUploadTargetProvider documentUploadTargetProvider) {

        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.documentProcessingDispatcher = documentProcessingDispatcher;
        this.documentStatusNotifier = documentStatusNotifier;
        this.storageService = storageService;
        this.documentUploadTargetProvider = documentUploadTargetProvider;
    }

    public DocumentUploadSessionResponse createUploadSession(UUID userId, DocumentUploadSessionRequest request) {
        var inputType = parseInputType(request.inputType());
        return switch (inputType) {
            case PDF -> createPdfUploadSession(userId, request.originalFilename());
            case TEXT -> createTextUploadSession(userId, request.text());
            case IMAGE -> throw new IllegalArgumentException("Image uploads are not yet supported.");
        };
    }

    public DocumentResponse uploadContent(UUID userId, UUID documentId, byte[] content) {
        var document = getOwnedDocument(userId, documentId);

        if (document.getInputType() != InputType.PDF) {
            throw new IllegalArgumentException("Only PDF documents can upload file content.");
        }

        // Only allow content upload when the document is waiting for it
        if (document.getStatus() != DocumentStatus.PENDING_UPLOAD
                && document.getStatus() != DocumentStatus.UPLOADING
                && document.getStatus() != DocumentStatus.FAILED) {
            throw new IllegalArgumentException(
                    "Cannot upload content for a document in status " + document.getStatus());
        }

        document.setStatus(DocumentStatus.UPLOADING);
        document.setErrorMessage(null);
        documentRepository.save(document);
        documentStatusNotifier.notify(documentId, ProcessingStatusEvent.uploading());

        try {
            var storageKey = storageService.store(document.getId(), new ByteArrayInputStream(content)).join();
            document.setStorageKey(storageKey);
            documentRepository.save(document);
            return documentService.toDocumentResponse(document);
        } catch (CompletionException ex) {
            var cause = ex.getCause() instanceof StorageSaveException sse
                    ? sse
                    : new StorageSaveException("Failed to store file", ex.getCause());
            log.error("Document upload failed", cause);
            markUploadFailed(documentId, document, cause);
            throw cause;
        } catch (StorageSaveException ex) {
            log.error("Document upload failed", ex);
            markUploadFailed(documentId, document, ex);
            throw ex;
        }
    }

    public DocumentResponse finaliseUpload(UUID userId, UUID documentId) {
        var document = getOwnedDocument(userId, documentId);

        if (document.getStatus() == DocumentStatus.QUEUED
                || document.getStatus() == DocumentStatus.PROCESSING
                || document.getStatus() == DocumentStatus.READY) {
            return documentService.toDocumentResponse(document);
        }

        if (document.getInputType() == InputType.PDF && (document.getStorageKey() == null || document.getStorageKey().isBlank())) {
            throw new DocumentNotReadyException("Document content has not been uploaded yet.");
        }

        try {
            if (document.getInputType() == InputType.PDF && !storageService.exists(document.getStorageKey()).join()) {
                throw new DocumentNotReadyException("Document content has not been uploaded yet.");
            }
        } catch (CompletionException ex) {
            log.error("Failed to verify document storage for {}", documentId, ex.getCause());
            throw new StorageSaveException("Unable to verify uploaded content. Please try again.", ex.getCause());
        }

        if (document.getInputType() == InputType.TEXT
                && (document.getExtractedText() == null || document.getExtractedText().isBlank())) {
            throw new DocumentNotReadyException("Text content is required before finalising the document.");
        }

        var updatedRows = documentRepository.transitionStatusIfCurrentStatusNotIn(
                documentId,
                userId,
                DocumentStatus.QUEUED,
                java.util.Set.of(DocumentStatus.QUEUED, DocumentStatus.PROCESSING, DocumentStatus.READY));

        if (updatedRows == 0) {
            return documentService.toDocumentResponse(getOwnedDocument(userId, documentId));
        }

        // Re-fetch after the atomic update (which clears the persistence context)
        // to ensure we have a fresh, consistent entity.
        document = getOwnedDocument(userId, documentId);

        documentStatusNotifier.notify(documentId, ProcessingStatusEvent.queued());
        documentProcessingDispatcher.dispatch(documentId);
        return documentService.toDocumentResponse(document);
    }

    private DocumentUploadSessionResponse createPdfUploadSession(UUID userId, String originalFilename) {
        var document = new Document();
        document.setUser(userRepository.getReferenceById(userId));
        document.setInputType(InputType.PDF);
        document.setOriginalFilename(originalFilename != null ? originalFilename : "");
        document.setStatus(DocumentStatus.PENDING_UPLOAD);
        documentRepository.save(document);

        DocumentUploadTargetDescriptor uploadTargetDescriptor;
        try {
            uploadTargetDescriptor = documentUploadTargetProvider.createUploadTarget(document.getId(), originalFilename);
        } catch (Exception ex) {
            // Clean up the orphaned document record if upload target creation fails
            // (e.g. S3 credentials invalid, presigning error).
            log.error("Failed to create upload target for document {}, cleaning up", document.getId(), ex);
            documentRepository.delete(document);
            throw ex;
        }

        if (uploadTargetDescriptor.storageKey() != null) {
            document.setStorageKey(uploadTargetDescriptor.storageKey());
            documentRepository.save(document);
        }

        return new DocumentUploadSessionResponse(
                documentService.toDocumentResponse(document),
                uploadTargetDescriptor.uploadTarget()
        );
    }

    private DocumentUploadSessionResponse createTextUploadSession(UUID userId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text uploads must include content.");
        }

        var document = new Document();
        document.setUser(userRepository.getReferenceById(userId));
        document.setInputType(InputType.TEXT);
        document.setExtractedText(text);
        document.setStatus(DocumentStatus.QUEUED);
        documentRepository.save(document);

        documentProcessingDispatcher.dispatch(document.getId());
        return new DocumentUploadSessionResponse(documentService.toDocumentResponse(document), null);
    }

    private Document getOwnedDocument(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    private static InputType parseInputType(String inputType) {
        if (inputType == null || inputType.isBlank()) {
            throw new IllegalArgumentException("inputType is required.");
        }

        try {
            return InputType.valueOf(inputType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported input type: " + inputType, ex);
        }
    }

    private void markUploadFailed(UUID documentId, Document document, StorageSaveException ex) {
        var errorMessage = "Failed to store file: " + ex.getMessage();
        document.setStatus(DocumentStatus.FAILED);
        document.setErrorMessage(errorMessage);
        documentRepository.save(document);
        documentStatusNotifier.notify(documentId, ProcessingStatusEvent.failed(errorMessage));
        documentStatusNotifier.complete(documentId);
    }
}
