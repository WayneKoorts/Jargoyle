package com.jargoyle.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jargoyle.dto.*;
import com.jargoyle.entity.*;
import com.jargoyle.repository.DocumentChunkRepository;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.service.storage.StorageLoadException;
import com.jargoyle.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.core.io.InputStreamResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocumentProcessingServiceTests {

    private DocumentRepository mockDocumentRepository;
    private DocumentChunkRepository mockDocumentChunkRepository;
    private DocumentSummaryRepository mockDocumentSummaryRepository;
    private ChunkingService mockChunkingService;
    private TextExtractionService mockTextExtractionService;
    private SummaryGenerationService mockSummaryGenerationService;
    private DocumentStatusNotifier mockDocumentStatusNotifier;
    private StorageService mockStorageService;
    private ObjectMapper objectMapper;

    private DocumentProcessingService sut;

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String EXTRACTED_TEXT = "This is a sample legal document with terms and conditions.";
    private static final String STORAGE_KEY = "documents/test-file.pdf";

    @BeforeEach
    void setUp() {
        mockDocumentRepository = mock(DocumentRepository.class);
        mockDocumentChunkRepository = mock(DocumentChunkRepository.class);
        mockDocumentSummaryRepository = mock(DocumentSummaryRepository.class);
        mockChunkingService = mock(ChunkingService.class);
        mockTextExtractionService = mock(TextExtractionService.class);
        mockSummaryGenerationService = mock(SummaryGenerationService.class);
        mockDocumentStatusNotifier = mock(DocumentStatusNotifier.class);
        mockStorageService = mock(StorageService.class);
        objectMapper = new ObjectMapper();

        sut = new DocumentProcessingService(
                mockDocumentRepository,
                mockDocumentChunkRepository,
                mockDocumentSummaryRepository,
                mockChunkingService,
                mockTextExtractionService,
                mockSummaryGenerationService,
                mockDocumentStatusNotifier,
                mockStorageService,
                objectMapper);
    }

    // ── Helper methods ──────────────────────────────────────────────

    private Document createDocument(InputType inputType) {
        var document = new Document();
        document.setInputType(inputType);
        document.setStorageKey(STORAGE_KEY);
        document.setStatus(DocumentStatus.UPLOADING);
        document.setExtractedText(EXTRACTED_TEXT);
        return document;
    }

    private DocumentSummaryResult createSummaryResult() {
        var keyFacts = new KeyFacts(
                List.of(new KeyFact("Total", "£500", "monthly payment")),
                List.of(new KeyFact("Start date", "01-01-2026", "contract begins")),
                List.of(new KeyFact("Provider", "Acme Corp", "service provider")));
        var flaggedTerms = List.of(new FlaggedTerm("indemnity", "A duty to compensate for loss or damage."));
        return new DocumentSummaryResult("A plain summary of the document.", keyFacts, flaggedTerms, "Sample Contract", "CONTRACT");
    }

    private void setUpDocumentFound(Document document) {
        // Use thenAnswer so that getId() returns the ID we expect, since the
        // JPA @GeneratedValue UUID isn't set outside of a persistence context.
        when(mockDocumentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(mockDocumentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void setUpPdfStorageAndExtraction() throws IOException {
        var pdfStream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(mockStorageService.load(STORAGE_KEY)).thenReturn(CompletableFuture.completedFuture(new InputStreamResource(pdfStream)));
        when(mockTextExtractionService.extractText(any(InputStream.class))).thenReturn(EXTRACTED_TEXT);
    }

    private void setUpChunking() {
        when(mockChunkingService.chunkText(EXTRACTED_TEXT)).thenReturn(List.of(
                new ChunkingService.TextChunk(0, "Chunk one", 25),
                new ChunkingService.TextChunk(1, "Chunk two", 20)));
        when(mockDocumentChunkRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Happy-path tests ────────────────────────────────────────────

    @Test
    void processDocument_pdfDocument_extractsTextAndGeneratesSummary() throws Exception {
        var document = createDocument(InputType.PDF);
        setUpDocumentFound(document);
        setUpPdfStorageAndExtraction();
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(createSummaryResult());

        sut.processDocument(DOCUMENT_ID);

        verify(mockTextExtractionService).extractText(any(InputStream.class));
        verify(mockChunkingService).chunkText(EXTRACTED_TEXT);
        verify(mockSummaryGenerationService).generateDocumentSummary(EXTRACTED_TEXT);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getTitle()).isEqualTo("Sample Contract");
    }

    @Test
    void processDocument_textDocument_usesExtractedTextDirectly() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(createSummaryResult());

        sut.processDocument(DOCUMENT_ID);

        verifyNoInteractions(mockStorageService);
        verifyNoInteractions(mockTextExtractionService);
        verify(mockChunkingService).chunkText(EXTRACTED_TEXT);
        verify(mockSummaryGenerationService).generateDocumentSummary(EXTRACTED_TEXT);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void processDocument_validDocument_persistsSummaryWithSerialisedJson() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        var summaryResult = createSummaryResult();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(summaryResult);

        sut.processDocument(DOCUMENT_ID);

        var summaryCaptor = ArgumentCaptor.forClass(DocumentSummary.class);
        verify(mockDocumentSummaryRepository).save(summaryCaptor.capture());

        var savedSummary = summaryCaptor.getValue();
        assertThat(savedSummary.getPlainSummary()).isEqualTo("A plain summary of the document.");
        assertThat(savedSummary.getDocument()).isSameAs(document);
        // Verify that flaggedTerms and keyFacts were serialised as JSON strings.
        assertThat(savedSummary.getFlaggedTerms()).contains("indemnity");
        assertThat(savedSummary.getKeyFacts()).contains("Acme Corp");
    }

    @Test
    void processDocument_validDocument_setsDocumentTypeFromSummaryResult() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(createSummaryResult());

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getDocumentType()).isEqualTo(DocumentType.CONTRACT);
    }

    // ── Status transition and notification tests ────────────────────

    @Test
    void processDocument_validDocument_transitionsStatusInCorrectOrder() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(createSummaryResult());

        // Capture the status at the moment each save occurs, because the
        // Document is mutated in place — an ArgumentCaptor would only see the
        // final state for every captured reference.
        var statusesAtSaveTime = new ArrayList<DocumentStatus>();
        when(mockDocumentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            statusesAtSaveTime.add(saved.getStatus());
            return saved;
        });

        sut.processDocument(DOCUMENT_ID);

        assertThat(statusesAtSaveTime.getFirst()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(statusesAtSaveTime.getLast()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void processDocument_validDocument_emitsNotificationsInOrder() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(createSummaryResult());

        sut.processDocument(DOCUMENT_ID);

        InOrder inOrder = inOrder(mockDocumentStatusNotifier);
        inOrder.verify(mockDocumentStatusNotifier).notify(eq(DOCUMENT_ID), argThat(e -> "PROCESSING".equals(e.status())));
        inOrder.verify(mockDocumentStatusNotifier).notify(eq(DOCUMENT_ID), argThat(e -> "READY".equals(e.status())));
        inOrder.verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    @Test
    void processDocument_always_callsCompleteOnNotifier() {
        // Even when document is not found, complete() should still be called.
        when(mockDocumentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

        sut.processDocument(DOCUMENT_ID);

        verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    @Test
    void processDocument_alreadyProcessing_skipsWithoutModification() {
        var document = createDocument(InputType.PDF);
        document.setStatus(DocumentStatus.PROCESSING);
        setUpDocumentFound(document);

        sut.processDocument(DOCUMENT_ID);

        // Should not re-save or re-process the document
        verify(mockSummaryGenerationService, never()).generateDocumentSummary(any());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void processDocument_alreadyReady_skipsWithoutModification() {
        var document = createDocument(InputType.TEXT);
        document.setStatus(DocumentStatus.READY);
        setUpDocumentFound(document);

        sut.processDocument(DOCUMENT_ID);

        verify(mockSummaryGenerationService, never()).generateDocumentSummary(any());
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void processDocument_exceptionWithNullMessage_usesClassNameAsFallback() {
        var document = createDocument(InputType.TEXT);
        document.setExtractedText(null); // Will cause NPE during getText
        setUpDocumentFound(document);
        when(mockChunkingService.chunkText(null)).thenReturn(List.of());
        when(mockSummaryGenerationService.generateDocumentSummary(null))
                .thenThrow(new NullPointerException());

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        // Should use class name since NPE.getMessage() returns null
        assertThat(document.getErrorMessage()).isEqualTo("NullPointerException");
    }

    // ── Error-handling tests ────────────────────────────────────────

    @Test
    void processDocument_documentNotFound_notifiesFailure() {
        when(mockDocumentRepository.findById(DOCUMENT_ID)).thenReturn(Optional.empty());

        sut.processDocument(DOCUMENT_ID);

        verify(mockDocumentStatusNotifier).notify(eq(DOCUMENT_ID), argThat(e -> "FAILED".equals(e.status())));
        // Document is null so repository.save should only be called for the
        // initial processing notification — not for setting FAILED status.
        verify(mockDocumentRepository, never()).save(any());
    }

    @Test
    void processDocument_storageLoadFails_setsStatusToFailed() throws Exception {
        var document = createDocument(InputType.PDF);
        setUpDocumentFound(document);
        when(mockStorageService.load(STORAGE_KEY)).thenThrow(new StorageLoadException("Storage unavailable"));

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).contains("not found");
        verify(mockDocumentStatusNotifier).notify(eq(DOCUMENT_ID), argThat(e -> "FAILED".equals(e.status())));
        verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    @Test
    void processDocument_summaryGenerationFails_setsStatusToFailed() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).contains("LLM service unavailable");
        verify(mockDocumentStatusNotifier).notify(eq(DOCUMENT_ID), argThat(e -> "FAILED".equals(e.status())));
        verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    @Test
    void processDocument_imageDocument_setsStatusToFailed() {
        var document = createDocument(InputType.IMAGE);
        setUpDocumentFound(document);

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).contains("not yet implemented");
        verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    @Test
    void processDocument_pdfExtractionFails_setsStatusToFailed() throws Exception {
        var document = createDocument(InputType.PDF);
        setUpDocumentFound(document);
        var pdfStream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(mockStorageService.load(STORAGE_KEY)).thenReturn(CompletableFuture.completedFuture(new InputStreamResource(pdfStream)));
        when(mockTextExtractionService.extractText(any(InputStream.class)))
                .thenThrow(new IOException("Corrupt PDF"));

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        verify(mockDocumentStatusNotifier).notify(eq(DOCUMENT_ID), argThat(e -> "FAILED".equals(e.status())));
        verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    @Test
    void processDocument_validDocument_persistsDocumentChunks() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        setUpChunking();
        when(mockSummaryGenerationService.generateDocumentSummary(EXTRACTED_TEXT)).thenReturn(createSummaryResult());

        sut.processDocument(DOCUMENT_ID);

        verify(mockDocumentChunkRepository).deleteByDocumentId(DOCUMENT_ID);

        var chunkCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockDocumentChunkRepository).saveAll(chunkCaptor.capture());

        @SuppressWarnings("unchecked")
        var savedChunks = (List<DocumentChunk>) chunkCaptor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks).extracting(DocumentChunk::getChunkIndex).containsExactly(0, 1);
        assertThat(savedChunks).extracting(DocumentChunk::getContent).containsExactly("Chunk one", "Chunk two");
        assertThat(savedChunks).extracting(DocumentChunk::getTokenCount).containsExactly(25, 20);
    }

    @Test
    void processDocument_chunkingFails_setsStatusToFailed() {
        var document = createDocument(InputType.TEXT);
        setUpDocumentFound(document);
        when(mockChunkingService.chunkText(EXTRACTED_TEXT)).thenThrow(new RuntimeException("Chunking failed"));

        sut.processDocument(DOCUMENT_ID);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).contains("Chunking failed");
        verify(mockSummaryGenerationService, never()).generateDocumentSummary(any());
    }
}
