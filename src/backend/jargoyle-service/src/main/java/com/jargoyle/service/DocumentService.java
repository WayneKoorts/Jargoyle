package com.jargoyle.service;

import com.jargoyle.dto.DocumentContentLocationResponse;
import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentSummaryResponse;
import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.entity.InputType;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.service.content.DocumentContentTargetProvider;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.storage.StorageService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jargoyle.repository.DocumentRepository;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final DocumentRepository documentRepository;
    private final DocumentSummaryRepository documentSummaryRepository;
    private final StorageService storageService;
    private final DocumentContentTargetProvider contentTargetProvider;

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentSummaryRepository documentSummaryRepository,
            StorageService storageService,
            DocumentContentTargetProvider contentTargetProvider) {

        this.documentRepository = documentRepository;
        this.documentSummaryRepository = documentSummaryRepository;
        this.storageService = storageService;
        this.contentTargetProvider = contentTargetProvider;
    }

    public DocumentResponse getById(UUID userId, UUID documentId) {
        log.debug("Retrieving document {} for user {}", documentId, userId);
        return documentRepository.findByIdAndUserId(documentId, userId)
            .map(this::toDocumentResponse)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    public Page<DocumentListResponse> list(UUID userId, Pageable pageable) {
        log.debug("Getting document list for user {}", userId);
        return documentRepository.findByUserId(userId, pageable)
            .map(d -> new DocumentListResponse(
                d.getId(),
                d.getTitle(),
                d.getDocumentType() != null ? d.getDocumentType().toString() : null,
                d.getInputType() != null ? d.getInputType().toString() : null,
                d.getStatus().toString(),
                d.getOriginalFilename(),
                truncate(d.getExtractedText(), 60),
                d.getCreatedAt()));
    }

    public DocumentResponse update(UUID userId, UUID documentId, DocumentUpdateRequest request) {
        log.debug("Updating document {}", documentId);
        var document = documentRepository.findByIdAndUserId(documentId, userId)
            .orElseThrow(() -> {
                log.warn("Unable to update document {} as it can't be found", documentId);
                return new DocumentNotFoundException(documentId);
            });

        request.title()
            .map(String::trim)
            .filter(title -> !title.isEmpty())
            .ifPresent(document::setTitle);

        request.documentType()
            .map(String::trim)
            .filter(type -> !type.isEmpty())
            .map(DocumentType::fromString)
            .ifPresent(document::setDocumentType);
        
        documentRepository.save(document);

        return toDocumentResponse(document);
    }

    /**
     * Returns the location from which the frontend can access the original document content.
     *
     * <p>For TEXT documents, the extracted text is returned inline. For file-based
     * documents (PDF, IMAGE), a URL is generated via the
     * {@link DocumentContentTargetProvider} — a presigned S3 URL in production or a
     * backend-relative URL in local development.</p>
     *
     * @param userId     the ID of the requesting user (for ownership verification)
     * @param documentId the ID of the document
     * @return a {@link DocumentContentLocationResponse} containing the content or a URL to fetch it
     * @throws DocumentNotFoundException if the document does not exist or does not belong to the user
     * @throws IllegalStateException     if a file-based document has no storage key
     */
    public DocumentContentLocationResponse getContentLocation(UUID userId, UUID documentId) {
        log.debug("Resolving content location for document {} for user {}", documentId, userId);
        var document = getDocumentEntity(userId, documentId);

        if (document.getInputType() == InputType.TEXT) {
            var text = document.getExtractedText() != null ? document.getExtractedText() : "";
            return new DocumentContentLocationResponse(null, text, "TEXT");
        }

        // PDF or IMAGE — delegate to the profile-specific content target provider
        var storageKey = document.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalStateException(
                    "Document %s has input type %s but no storage key".formatted(documentId, document.getInputType()));
        }

        var url = contentTargetProvider.createContentUrl(documentId, storageKey, document.getOriginalFilename());
        return new DocumentContentLocationResponse(url, null, document.getInputType().toString());
    }

    /**
     * Retrieves the document entity after verifying ownership.
     *
     * @param userId     the ID of the requesting user
     * @param documentId the ID of the document
     * @return the document entity
     * @throws DocumentNotFoundException if the document does not exist or does not belong to the user
     */
    public Document getDocumentEntity(UUID userId, UUID documentId) {
        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        // Fetch the document first so we can clean up its stored file.
        var document = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        documentRepository.delete(document);

        // Delete the stored file after the DB row is removed. StorageService.delete()
        // is best-effort (logs failures but doesn't throw), so a storage failure
        // won't roll back the database deletion.
        if (document.getStorageKey() != null) {
            storageService.delete(document.getStorageKey());
        }
    }

    /** Returns the first {@code maxLength} characters of {@code text}, appending "…" if truncated. */
    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "…";
    }

    // Package-private so DocumentIngestionService can reuse the mapping logic.
    DocumentResponse toDocumentResponse(Document doc) {
        var docSummaryResponse = documentSummaryRepository.findByDocumentId(doc.getId())
                .map(summary -> new DocumentSummaryResponse(
                        summary.getPlainSummary(),
                        summary.getKeyFacts(),
                        summary.getFlaggedTerms()
                ))
                .orElse(null);

        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getDocumentType() != null ? doc.getDocumentType().toString() : null,
                doc.getInputType().toString(),
                doc.getOriginalFilename(),
                doc.getStatus().toString(),
                doc.getErrorMessage(),
                docSummaryResponse,
                doc.getCreatedAt()
        );
    }
}
