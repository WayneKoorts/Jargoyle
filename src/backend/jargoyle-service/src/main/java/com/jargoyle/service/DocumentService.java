package com.jargoyle.service;

import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentSummaryResponse;
import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.repository.DocumentSummaryRepository;
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

    public DocumentService(
            DocumentRepository documentRepository,
            DocumentSummaryRepository documentSummaryRepository,
            StorageService storageService) {

        this.documentRepository = documentRepository;
        this.documentSummaryRepository = documentSummaryRepository;
        this.storageService = storageService;
    }

    public DocumentResponse getById(UUID userId, UUID documentId) {
        log.debug("Retrieving document {} for user {}", documentId, userId);
        return documentRepository.findByIdAndUserId(documentId, userId)
            .map(this::toDocumentResponse)
            .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    public Page<DocumentListResponse> list(UUID userId, Pageable pageable) {
        log.debug("Getting document list for user {}", userId);
        return documentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
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
