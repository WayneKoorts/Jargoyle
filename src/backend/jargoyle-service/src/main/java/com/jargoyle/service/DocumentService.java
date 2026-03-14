package com.jargoyle.service;

import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentSummaryResponse;
import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.DocumentType;
import com.jargoyle.entity.InputType;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.storage.StorageSaveException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.service.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentSummaryRepository documentSummaryRepository;
    private final DocumentProcessingService documentProcessingService;
    private final StorageService storageService;

    public DocumentService(
            UserRepository userRepository,
            DocumentRepository documentRepository,
            DocumentSummaryRepository documentSummaryRepository,
            DocumentProcessingService documentProcessingService,
            StorageService storageService) {

        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.documentSummaryRepository = documentSummaryRepository;
        this.documentProcessingService = documentProcessingService;
        this.storageService = storageService;
    }

    public DocumentResponse upload(DocumentUploadRequest request) throws StorageSaveException {
        log.debug("Received upload request");
        return switch (request) {
            case DocumentUploadRequest.PdfDocumentUpload pdfUploadRequest -> handlePdfUpload(pdfUploadRequest);
            case DocumentUploadRequest.TextDocumentUpload textUploadRequest -> handleTextUpload(textUploadRequest);
        };
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
                d.getCreatedAt()));
    }

    public DocumentResponse update(UUID userId, UUID documentId, DocumentUpdateRequest request) {
        log.debug("Updating document {}", documentId);
        var document = documentRepository.findByIdAndUserId(documentId, userId)
            .orElseThrow(() -> {
                log.warn("Unable to update document {} as it can't be found", documentId);
                return new DocumentNotFoundException(documentId);
            });

        document.setTitle(request.title());
        document.setDocumentType(DocumentType.fromString(request.documentType()));
        documentRepository.save(document);

        return toDocumentResponse(document);
    }

    public void delete(UUID userId, UUID documentId) {
        documentRepository.deleteByIdAndUserId(documentId, userId);
    }

    /**
     * <p>Creates a new {@link Document} entity with basic attributes (e.g. input type and doc status), saves
     * the PDF to the storage service, attaches the storage key to a new {@link Document}, and returns the
     * result of calling {@link DocumentService#submitForProcessing(Document)}. </p>
     *
     * @param pdfUploadRequest contains the user ID, original filename of the PDF, and the PDF contents.
     * @return {@link DocumentResponse} DTO with details of the newly-created document.
     * @throws StorageSaveException if the document couldn't be saved.
     */
    private DocumentResponse handlePdfUpload(
            DocumentUploadRequest.PdfDocumentUpload pdfUploadRequest)
                throws StorageSaveException {

        var document = new Document();
        document.setUser(userRepository.getReferenceById(pdfUploadRequest.userId()));
        document.setInputType(InputType.PDF);
        document.setStatus(DocumentStatus.UPLOADING);
        document.setOriginalFilename(pdfUploadRequest.originalFilename());
        documentRepository.save(document);

        try {
            var storageKey = storageService.store(document.getId(), new ByteArrayInputStream(pdfUploadRequest.content()));
            document.setStorageKey(storageKey);
            documentRepository.save(document);
        } catch (StorageSaveException ex) {
            log.error("Document upload failed", ex);
            document.setStatus(DocumentStatus.FAILED);
            document.setErrorMessage("Failed to store file: " + ex.getMessage());
            documentRepository.save(document);
            throw ex;
        }

        return submitForProcessing(document);
    }

    /**
     * Creates a new {@link Document} entity, sets status and initial related data (e.g. {@link com.jargoyle.entity.User})
     * and attaches the text to the entity before passing to {@link DocumentService#submitForProcessing(Document)}
     *
     * @param textUploadRequest contains the user ID and text content.
     * @return {@link DocumentResponse} DTO with details of the newly-created document.
     */
    private DocumentResponse handleTextUpload(DocumentUploadRequest.TextDocumentUpload textUploadRequest) {

        var document = new Document();
        document.setInputType(InputType.TEXT);
        document.setUser(userRepository.getReferenceById(textUploadRequest.userId()));
        document.setStatus(DocumentStatus.UPLOADING);
        document.setExtractedText(textUploadRequest.pastedText());
        documentRepository.save(document);

        return submitForProcessing(document);
    }

    /**
     * Passes the document to the {@link DocumentProcessingService} to start the async processing and
     * assembles a new {@link DocumentResponse} DTO with details of the document.
     *
     * @param document {@link Document} from one of the InputType-specific upload methods.
     * @return {@link DocumentResponse} DTO
     */
    private DocumentResponse submitForProcessing(Document document) {
        // Hand off to DocumentProcessingService for async processing.
        log.debug("Submitting document to DocumentProcessingService");
        documentProcessingService.processDocument(document.getId());

        return toDocumentResponse(document);
    }

    private DocumentResponse toDocumentResponse(Document doc) {
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
