package com.jargoyle.service;

import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.entity.*;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.DocumentSummaryRepository;
import com.jargoyle.service.content.DocumentContentTargetProvider;
import com.jargoyle.service.exception.DocumentNotFoundException;
import com.jargoyle.service.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class DocumentServiceTests {

    private DocumentRepository mockDocumentRepository;
    private DocumentSummaryRepository mockDocumentSummaryRepository;
    private StorageService mockStorageService;
    private DocumentContentTargetProvider mockContentTargetProvider;

    private DocumentService sut;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockDocumentRepository = mock(DocumentRepository.class);
        mockDocumentSummaryRepository = mock(DocumentSummaryRepository.class);
        mockStorageService = mock(StorageService.class);
        mockContentTargetProvider = mock(DocumentContentTargetProvider.class);

        sut = new DocumentService(
                mockDocumentRepository,
                mockDocumentSummaryRepository,
                mockStorageService,
                mockContentTargetProvider);
    }

    // ── Helper methods ──────────────────────────────────────────────

    private Document createDocument() {
        var document = new Document();
        document.setInputType(InputType.PDF);
        document.setStatus(DocumentStatus.READY);
        document.setDocumentType(DocumentType.CONTRACT);
        document.setTitle("Test Document");
        document.setOriginalFilename("test.pdf");
        document.setStorageKey("documents/test-file.pdf");
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
        when(mockDocumentRepository.findByUserId(USER_ID, pageable))
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
        when(mockDocumentRepository.findByUserId(USER_ID, pageable))
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
        var request = new DocumentUpdateRequest(Optional.of("New Title"), Optional.of("BILL"));

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
        var request = new DocumentUpdateRequest(Optional.of("Updated Title"), Optional.of("MORTGAGE"));

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
        var request = new DocumentUpdateRequest(Optional.of("Title"), Optional.of("NONSENSE"));

        var result = sut.update(USER_ID, DOCUMENT_ID, request);

        assertThat(result.documentType()).isEqualTo("OTHER");
    }

    @Test
    void update_documentNotFound_throwsDocumentNotFoundException() {
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        var request = new DocumentUpdateRequest(Optional.of("Title"), Optional.of("BILL"));

        assertThatThrownBy(() -> sut.update(USER_ID, DOCUMENT_ID, request))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining(DOCUMENT_ID.toString());
    }

    // ── delete tests ────────────────────────────────────────────────

    @Test
    void delete_documentWithStorageKey_deletesFromStorageAndDatabase() {
        var document = createDocument();
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));

        sut.delete(USER_ID, DOCUMENT_ID);

        verify(mockDocumentRepository).delete(document);
        verify(mockStorageService).delete("documents/test-file.pdf");
    }

    @Test
    void delete_documentWithoutStorageKey_skipsStorageDeletion() {
        var document = createDocument();
        document.setStorageKey(null);
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));

        sut.delete(USER_ID, DOCUMENT_ID);

        verify(mockDocumentRepository).delete(document);
        verifyNoInteractions(mockStorageService);
    }

    @Test
    void delete_documentNotFound_throwsDocumentNotFoundException() {
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.delete(USER_ID, DOCUMENT_ID))
                .isInstanceOf(DocumentNotFoundException.class);

        verifyNoInteractions(mockStorageService);
    }

    // ── getContentLocation tests ─────────────────────────────────────

    @Test
    void getContentLocation_pdfDocument_returnsUrlFromContentTargetProvider() {
        var document = createDocument();
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));
        when(mockContentTargetProvider.createContentUrl(any(), eq("documents/test-file.pdf"), eq("test.pdf")))
                .thenReturn("https://s3.example.com/presigned-url");

        var result = sut.getContentLocation(USER_ID, DOCUMENT_ID);

        assertThat(result.url()).isEqualTo("https://s3.example.com/presigned-url");
        assertThat(result.text()).isNull();
        assertThat(result.inputType()).isEqualTo("PDF");
    }

    @Test
    void getContentLocation_textDocument_returnsInlineText() {
        var document = createDocument();
        document.setInputType(InputType.TEXT);
        document.setStorageKey(null);
        document.setExtractedText("Hello, world!");
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));

        var result = sut.getContentLocation(USER_ID, DOCUMENT_ID);

        assertThat(result.url()).isNull();
        assertThat(result.text()).isEqualTo("Hello, world!");
        assertThat(result.inputType()).isEqualTo("TEXT");
        verifyNoInteractions(mockContentTargetProvider);
    }

    @Test
    void getContentLocation_textDocumentWithNullExtractedText_returnsEmptyString() {
        var document = createDocument();
        document.setInputType(InputType.TEXT);
        document.setStorageKey(null);
        document.setExtractedText(null);
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));

        var result = sut.getContentLocation(USER_ID, DOCUMENT_ID);

        assertThat(result.text()).isEmpty();
    }

    @Test
    void getContentLocation_documentNotFound_throwsDocumentNotFoundException() {
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.getContentLocation(USER_ID, DOCUMENT_ID))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void getContentLocation_pdfWithNoStorageKey_throwsIllegalStateException() {
        var document = createDocument();
        document.setStorageKey(null);
        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document));

        assertThatThrownBy(() -> sut.getContentLocation(USER_ID, DOCUMENT_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no storage key");
    }

}
