package com.jargoyle.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jargoyle.dto.DocumentSummaryResult;
import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.entity.*;
import com.jargoyle.repository.DocumentChunkRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.exception.DocumentProcessingException;
import com.jargoyle.service.storage.StorageLoadException;
import com.jargoyle.service.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.jargoyle.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * Asynchronous document processing pipeline. Runs on a dedicated thread pool
 * ({@code documentProcessingExecutor}) and orchestrates the full lifecycle of a
 * document from upload to ready: text extraction, chunking, embedding
 * generation, LLM summary generation, and persistence of results.
 *
 * <p>Each pipeline step emits {@link ProcessingStatusEvent} notifications via
 * {@link DocumentStatusNotifier} so connected SSE clients can display progress.
 * The document entity's status is also updated at each stage so that polling
 * clients see accurate state.
 *
 * <p>This service is the last line of defence for error handling on the async
 * thread — any unhandled exception is caught, logged, and recorded as a
 * {@link DocumentStatus#FAILED} status on the document entity.
 *
 * @see DocumentStatusNotifier
 * @see EmbeddingService
 * @see SummaryGenerationService
 * @see TextExtractionService
 */
@Service
public class DocumentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingService.class);
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentSummaryRepository documentSummaryRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final TextExtractionService textExtractionService;
    private final SummaryGenerationService summaryGenerationService;
    private final DocumentStatusNotifier documentStatusNotifier;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public DocumentProcessingService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            DocumentSummaryRepository documentSummaryRepository,
            ChunkingService chunkingService,
            EmbeddingService embeddingService,
            TextExtractionService textExtractionService,
            SummaryGenerationService summaryGenerationService,
            DocumentStatusNotifier documentStatusNotifier,
            StorageService storageService,
            ObjectMapper objectMapper) {

        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.documentSummaryRepository = documentSummaryRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.textExtractionService = textExtractionService;
        this.summaryGenerationService = summaryGenerationService;
        this.documentStatusNotifier = documentStatusNotifier;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a document through the full pipeline: text extraction, LLM summary
     * generation, and persistence of results. Intended to be called asynchronously
     * after a document has been uploaded and stored.
     *
     * <p>The pipeline transitions the document through {@code UPLOADING} →
     * {@code PROCESSING} → {@code READY} (or {@code FAILED} on error). Status
     * events are emitted at each step via {@link DocumentStatusNotifier}.
     *
     * @param documentId the ID of the document to process; the document must
     *                   already exist in the database and should have a status
     *                   of {@code UPLOADING}
     */
    public void processDocument(UUID documentId) {
        Document document = null;
        try {
            documentStatusNotifier.notify(documentId, ProcessingStatusEvent.processing("Generating document summary..."));

            var documentResult = documentRepository.findById(documentId);
            if (documentResult.isEmpty()) {
                throw new DocumentNotFoundException(documentId);
            }

            document = documentResult.get();
            if (document.getStatus() == DocumentStatus.PROCESSING || document.getStatus() == DocumentStatus.READY) {
                log.debug("Skipping processing for document {} with status {}", documentId, document.getStatus());
                return;
            }

            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);

            String textToBeProcessed;

            try {
                textToBeProcessed = getDocumentText(document);
            } catch (IOException ex) {
                throw new DocumentProcessingException(documentId, ex);
            }

            createDocumentChunks(documentId, document, textToBeProcessed);
            generateChunkEmbeddings(documentId);
            var documentSummaryResult = summaryGenerationService.generateDocumentSummary(textToBeProcessed);

            updateDocument(documentId, document, documentSummaryResult, textToBeProcessed);
            createDocumentSummary(document, documentSummaryResult);

            documentStatusNotifier.notify(documentId, ProcessingStatusEvent.ready());
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
        } catch (Exception ex) {
            log.error("An error occurred during document processing.", ex);
            // Provide a fallback message for exceptions that return null from getMessage()
            // (e.g. NullPointerException), so the user always sees something meaningful.
            var errorMessage = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            documentStatusNotifier.notify(documentId, ProcessingStatusEvent.failed(errorMessage));

            if (document != null) {
                document.setStatus(DocumentStatus.FAILED);
                document.setErrorMessage(errorMessage);
                documentRepository.save(document);
            }
        } finally {
            documentStatusNotifier.complete(documentId);
        }
    }

    private String getDocumentText(Document document) throws IOException {

        String textToBeProcessed;
        // If the document is a PDF, extract its text.
        if (document.getInputType() == InputType.PDF) {
            log.debug("PDF detected for text extraction");

            // Get the raw document out of storage.
            InputStream rawDocumentStream;
            try {
                rawDocumentStream = storageService
                        .load(document.getStorageKey())
                        .join()
                        .getInputStream();
            } catch (CompletionException ex) {
                // Unwrap: the original StorageLoadException is inside the CompletionException
                log.error("Unable to retrieve document", ex.getCause());
                throw new DocumentNotFoundException(document.getId(), ex.getCause());
            } catch (StorageLoadException | IOException ex) {
                log.error("Unable to retrieve document", ex);
                throw new DocumentNotFoundException(document.getId(), ex);
            }

            try {
                textToBeProcessed = textExtractionService.extractText(rawDocumentStream);
            } catch (IOException ex) {
                log.error("Unable to extract text from PDF", ex);
                throw new DocumentProcessingException(document.getId(), ex);
            }
        } else if (document.getInputType() == InputType.TEXT) {
            // No document stored in the storage service, it's just on the document.
            textToBeProcessed = document.getExtractedText();
        } else if (document.getInputType() == InputType.IMAGE) {
            throw new UnsupportedOperationException("Image processing is not yet implemented.");
        } else {
            throw new DocumentProcessingException(
                    String.format("Unknown document type \"%s\".  Document ID: \"%s\"", document.getInputType(), document.getId()));
        }

        return textToBeProcessed;
    }

    private void updateDocument(
            UUID documentId,
            Document document,
            DocumentSummaryResult documentSummaryResult,
            String textToBeProcessed) {

        // Update document properties with LLM-generated values.
        document.setTitle(documentSummaryResult.title());
        document.setDocumentType(DocumentType.fromString(documentSummaryResult.documentType()));
        document.setExtractedText(textToBeProcessed);
        documentRepository.save(document);
    }

    private void createDocumentSummary(
            Document document,
            DocumentSummaryResult documentSummaryResult) throws
                JsonProcessingException {

        // Save a new document summary object.
        var documentSummary = new DocumentSummary();
        documentSummary.setDocument(document);
        documentSummary.setPlainSummary(documentSummaryResult.plainSummary());
        var termsJson = objectMapper.writeValueAsString(documentSummaryResult.flaggedTerms());
        documentSummary.setFlaggedTerms(termsJson);
        var keyFactsJson = objectMapper.writeValueAsString(documentSummaryResult.keyFacts());
        documentSummary.setKeyFacts(keyFactsJson);
        documentSummaryRepository.save(documentSummary);
    }

    /**
     * Rebuilds the stored chunk list for a document from its extracted text.
     *
     * <p>Existing chunks are deleted first so a retry or reprocessing run does
     * not leave duplicate rows behind. Embeddings are intentionally not set here;
     * they are populated in the later embedding step.
     */
    private void createDocumentChunks(UUID documentId, Document document, String extractedText) {
        documentChunkRepository.deleteByDocumentId(documentId);

        var documentChunks = chunkingService.chunkText(extractedText)
            .stream()
            .map(textChunk -> {
                var documentChunk = new DocumentChunk();
                documentChunk.setDocument(document);
                documentChunk.setChunkIndex(textChunk.index());
                documentChunk.setContent(textChunk.content());
                documentChunk.setTokenCount(textChunk.tokenCount());
                return documentChunk;
            })
            .toList();

        documentChunkRepository.saveAll(documentChunks);
    }

    /**
     * Generates vector embeddings for all chunks of a document and persists
     * them. The chunks must already exist in the database (created by
     * {@link #createDocumentChunks}).
     *
     * <p>All chunk texts are embedded in a single batch API call to minimise
     * HTTP round-trips. Each chunk entity is then updated with its embedding
     * and saved back.
     */
    private void generateChunkEmbeddings(UUID documentId) {
        var chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        if (chunks.isEmpty()) {
            return;
        }

        var texts = chunks.stream()
                .map(DocumentChunk::getContent)
                .toList();

        var embeddings = embeddingService.embedBatch(texts);

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(embeddings.get(i));
        }

        documentChunkRepository.saveAll(chunks);
    }
}
