package com.jargoyle.service;

import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.entity.*;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.storage.StorageSaveException;
import com.jargoyle.service.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class DocumentServiceTests {

    private UserRepository mockUserRepository;
    private DocumentRepository mockDocumentRepository;
    private DocumentSummaryRepository mockDocumentSummaryRepository;
    private DocumentProcessingService mockDocumentProcessingService;
    private StorageService mockStorageService;

    private DocumentService sut;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final String STORAGE_KEY = "documents/test-file.pdf";

    @BeforeEach
    void setUp() {
        mockUserRepository = mock(UserRepository.class);
        mockDocumentRepository = mock(DocumentRepository.class);
        mockDocumentSummaryRepository = mock(DocumentSummaryRepository.class);
        mockDocumentProcessingService = mock(DocumentProcessingService.class);
        mockStorageService = mock(StorageService.class);

        sut = new DocumentService(
                mockUserRepository,
                mockDocumentRepository,
                mockDocumentSummaryRepository,
                mockDocumentProcessingService,
                mockStorageService);
    }

    // ── Helper methods ──────────────────────────────────────────────

    private Document createDocument() {
        var document = new Document();
        document.setInputType(InputType.PDF);
        document.setStatus(DocumentStatus.READY);
        document.setDocumentType(DocumentType.CONTRACT);
        document.setTitle("Test Document");
        document.setOriginalFilename("test.pdf");
        document.setStorageKey(STORAGE_KEY);
        return document;
    }

    private DocumentSummary createDocumentSummary(Document document) {
        var summary = new DocumentSummary();
        summary.setDocument(document);
        summary.setPlainSummary("A plain summary.");
        summary.setKeyFacts("{\"numbers\": []}");
        summary.setFlaggedTerms("[{\"term\": \"indemnity\"}]");
        return summary;
    }

    private User createUser() {
        var user = new User();
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        return user;
    }

    // ── getById tests ───────────────────────────────────────────────

    @Test
    void getById_documentExists_returnsDocumentResponse() {
        var document = createDocument();
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());

        var result = sut.getById(USER_ID, DOCUMENT_ID);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Test Document");
        assertThat(result.documentType()).isEqualTo("CONTRACT");
        assertThat(result.inputType()).isEqualTo("PDF");
        assertThat(result.originalFilename()).isEqualTo("test.pdf");
        assertThat(result.status()).isEqualTo("READY");
    }

    @Test
    void getById_documentExists_includesSummaryWhenPresent() {
        var document = createDocument();
        var summary = createDocumentSummary(document);
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.of(summary));

        var result = sut.getById(USER_ID, DOCUMENT_ID);

        assertThat(result.summary()).isNotNull();
        assertThat(result.summary().plainSummary()).isEqualTo("A plain summary.");
        assertThat(result.summary().keyFacts()).isEqualTo("{\"numbers\": []}");
        assertThat(result.summary().flaggedTerms()).isEqualTo("[{\"term\": \"indemnity\"}]");
    }

    @Test
    void getById_documentNotFound_throwsDocumentNotFoundException() {
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getById(USER_ID, DOCUMENT_ID))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(DOCUMENT_ID.toString());
    }

    @Test
    void getById_documentWithNullDocumentType_returnsNullDocumentType() {
        var document = createDocument();
        document.setDocumentType(null);
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());

        var result = sut.getById(USER_ID, DOCUMENT_ID);

        assertThat(result.documentType()).isNull();
    }

    // ── list tests ──────────────────────────────────────────────────

    @Test
    void list_documentsExist_returnsMappedPage() {
        var document = createDocument();
        var pageable = PageRequest.of(0, 10);
        var page = new PageImpl<>(List.of(document), pageable, 1);
        when(mockDocumentRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .thenReturn(page);

        var result = sut.list(USER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        var item = result.getContent().getFirst();
        assertThat(item.title()).isEqualTo("Test Document");
        assertThat(item.documentType()).isEqualTo("CONTRACT");
        assertThat(item.inputType()).isEqualTo("PDF");
        assertThat(item.status()).isEqualTo("READY");
    }

    @Test
    void list_noDocuments_returnsEmptyPage() {
        var pageable = PageRequest.of(0, 10);
        Page<Document> emptyPage = new PageImpl<>(List.of(), pageable, 0);
        when(mockDocumentRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .thenReturn(emptyPage);

        var result = sut.list(USER_ID, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ── update tests ────────────────────────────────────────────────

    @Test
    void update_documentExists_updatesAndReturnsResponse() {
        var document = createDocument();
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());
        var request = new DocumentUpdateRequest("New Title", "BILL");

        var result = sut.update(USER_ID, DOCUMENT_ID, request);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("New Title");
        assertThat(result.documentType()).isEqualTo("BILL");
    }

    @Test
    void update_documentExists_savesUpdatedEntity() {
        var document = createDocument();
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());
        var request = new DocumentUpdateRequest("Updated Title", "MORTGAGE");

        sut.update(USER_ID, DOCUMENT_ID, request);

        var captor = ArgumentCaptor.forClass(Document.class);
        verify(mockDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Updated Title");
        assertThat(captor.getValue().getDocumentType()).isEqualTo(DocumentType.MORTGAGE);
    }

    @Test
    void update_invalidDocumentType_fallsBackToOther() {
        var document = createDocument();
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());
        var request = new DocumentUpdateRequest("Title", "NONSENSE");

        var result = sut.update(USER_ID, DOCUMENT_ID, request);

        assertThat(result.documentType()).isEqualTo("OTHER");
    }

    @Test
    void update_documentNotFound_throwsDocumentNotFoundException() {
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        var request = new DocumentUpdateRequest("Title", "BILL");

        assertThatThrownBy(() -> sut.update(USER_ID, DOCUMENT_ID, request))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(DOCUMENT_ID.toString());
    }

    // ── delete tests ────────────────────────────────────────────────

    @Test
    void delete_callsRepositoryWithCorrectArguments() {
        sut.delete(USER_ID, DOCUMENT_ID);

        verify(mockDocumentRepository).deleteByIdAndUserId(DOCUMENT_ID, USER_ID);
    }

    // ── upload (PDF) tests ──────────────────────────────────────────

    @Test
    void upload_pdf_savesDocumentAndStoresFile() throws Exception {
        var user = createUser();
        var pdfContent = new byte[]{1, 2, 3};
        var request = new DocumentUploadRequest.PdfDocumentUpload(USER_ID, "file.pdf", pdfContent);

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockStorageService.store(any(), any(InputStream.class))).thenReturn(STORAGE_KEY);
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());

        var result = sut.upload(request);

        assertThat(result).isNotNull();
        assertThat(result.inputType()).isEqualTo("PDF");
        assertThat(result.originalFilename()).isEqualTo("file.pdf");
        verify(mockStorageService).store(any(), any(InputStream.class));
        verify(mockDocumentProcessingService).processDocument(any());
    }

    @Test
    void upload_pdf_setsInitialStatusToUploading() throws Exception {
        var user = createUser();
        var request = new DocumentUploadRequest.PdfDocumentUpload(USER_ID, "file.pdf", new byte[]{1});

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockStorageService.store(any(), any(InputStream.class))).thenReturn(STORAGE_KEY);
        // Capture the Document at first save to verify its initial status.
        when(mockDocumentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            return saved;
        });
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());

        sut.upload(request);

        var captor = ArgumentCaptor.forClass(Document.class);
        verify(mockDocumentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getFirst().getStatus()).isEqualTo(DocumentStatus.UPLOADING);
    }

    @Test
    void upload_pdfStorageFails_setsStatusToFailedAndRethrows() throws Exception {
        var user = createUser();
        var request = new DocumentUploadRequest.PdfDocumentUpload(USER_ID, "file.pdf", new byte[]{1});

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockStorageService.store(any(), any(InputStream.class)))
                .thenThrow(new StorageSaveException("Disk full"));

        assertThatThrownBy(() -> sut.upload(request))
                .isInstanceOf(StorageSaveException.class);

        var captor = ArgumentCaptor.forClass(Document.class);
        verify(mockDocumentRepository, atLeastOnce()).save(captor.capture());
        var lastSaved = captor.getAllValues().getLast();
        assertThat(lastSaved.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(lastSaved.getErrorMessage()).contains("Disk full");
    }

    @Test
    void upload_pdfStorageFails_doesNotSubmitForProcessing() throws Exception {
        var user = createUser();
        var request = new DocumentUploadRequest.PdfDocumentUpload(USER_ID, "file.pdf", new byte[]{1});

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockStorageService.store(any(), any(InputStream.class)))
                .thenThrow(new StorageSaveException("Disk full"));

        try { sut.upload(request); } catch (StorageSaveException ignored) {}

        verifyNoInteractions(mockDocumentProcessingService);
    }

    // ── upload (text) tests ─────────────────────────────────────────

    @Test
    void upload_text_savesDocumentAndSubmitsForProcessing() throws Exception {
        var user = createUser();
        var request = new DocumentUploadRequest.TextDocumentUpload(USER_ID, "Some pasted text");

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());

        var result = sut.upload(request);

        assertThat(result).isNotNull();
        assertThat(result.inputType()).isEqualTo("TEXT");
        verifyNoInteractions(mockStorageService);
        verify(mockDocumentProcessingService).processDocument(any());
    }

    @Test
    void upload_text_setsExtractedTextFromRequest() throws Exception {
        var user = createUser();
        var pastedText = "This is the user's pasted text.";
        var request = new DocumentUploadRequest.TextDocumentUpload(USER_ID, pastedText);

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentRepository.save(any(Document.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mockDocumentSummaryRepository.findByDocumentId(any()))
                .thenReturn(Optional.empty());

        sut.upload(request);

        var captor = ArgumentCaptor.forClass(Document.class);
        verify(mockDocumentRepository).save(captor.capture());
        assertThat(captor.getValue().getExtractedText()).isEqualTo(pastedText);
    }
}
