package com.jargoyle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentUploadSessionRequest;
import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.entity.Document;
import com.jargoyle.entity.DocumentStatus;
import com.jargoyle.entity.InputType;
import com.jargoyle.entity.User;
import com.jargoyle.repository.DocumentRepository;
import com.jargoyle.repository.UserRepository;
import com.jargoyle.service.exception.DocumentNotReadyException;
import com.jargoyle.service.storage.StorageSaveException;
import com.jargoyle.service.storage.StorageService;
import com.jargoyle.service.upload.DocumentUploadTargetDescriptor;
import com.jargoyle.service.upload.DocumentUploadTargetProvider;
import com.jargoyle.dto.DocumentUploadTargetResponse;

class DocumentIngestionServiceTests {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DOCUMENT_ID = UUID.randomUUID();

    private UserRepository mockUserRepository;
    private DocumentRepository mockDocumentRepository;
    private DocumentService mockDocumentService;
    private DocumentProcessingDispatcher mockDocumentProcessingDispatcher;
    private DocumentStatusNotifier mockDocumentStatusNotifier;
    private StorageService mockStorageService;
    private DocumentUploadTargetProvider mockDocumentUploadTargetProvider;

    private DocumentIngestionService sut;

    @BeforeEach
    void setUp() {
        mockUserRepository = mock(UserRepository.class);
        mockDocumentRepository = mock(DocumentRepository.class);
        mockDocumentService = mock(DocumentService.class);
        mockDocumentProcessingDispatcher = mock(DocumentProcessingDispatcher.class);
        mockDocumentStatusNotifier = mock(DocumentStatusNotifier.class);
        mockStorageService = mock(StorageService.class);
        mockDocumentUploadTargetProvider = mock(DocumentUploadTargetProvider.class);

        sut = new DocumentIngestionService(
                mockUserRepository,
                mockDocumentRepository,
                mockDocumentService,
                mockDocumentProcessingDispatcher,
                mockDocumentStatusNotifier,
                mockStorageService,
                mockDocumentUploadTargetProvider);

        when(mockDocumentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            if (document.getId() == null) {
                setDocumentId(document, DOCUMENT_ID);
            }
            return document;
        });
        when(mockDocumentService.toDocumentResponse(any(Document.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
    }

    @Test
    void createUploadSession_pdf_createsPendingDocumentAndReturnsTarget() {
        var user = createUser();
        var request = new DocumentUploadSessionRequest("PDF", "contract.pdf", null);

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentUploadTargetProvider.createUploadTarget(eq(DOCUMENT_ID), eq("contract.pdf")))
                .thenReturn(new DocumentUploadTargetDescriptor(
                        null,
                        new DocumentUploadTargetResponse("/documents/%s/content".formatted(DOCUMENT_ID), "PUT")));

        var response = sut.createUploadSession(USER_ID, request);

        assertThat(response.document().id()).isEqualTo(DOCUMENT_ID);
        assertThat(response.document().status()).isEqualTo("PENDING_UPLOAD");
        assertThat(response.uploadTarget()).isNotNull();
        assertThat(response.uploadTarget().url()).isEqualTo("/documents/%s/content".formatted(DOCUMENT_ID));
        verify(mockDocumentUploadTargetProvider).createUploadTarget(DOCUMENT_ID, "contract.pdf");
        verify(mockDocumentProcessingDispatcher, never()).dispatch(any(UUID.class));
    }

    @Test
    void createUploadSession_pdf_withStorageKey_persistsStorageKey() {
        var user = createUser();
        var request = new DocumentUploadSessionRequest("PDF", "contract.pdf", null);

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentUploadTargetProvider.createUploadTarget(eq(DOCUMENT_ID), eq("contract.pdf")))
                .thenReturn(new DocumentUploadTargetDescriptor(
                        "documents/%s/file.pdf".formatted(DOCUMENT_ID),
                        new DocumentUploadTargetResponse("https://example.com/upload", "PUT")));

        sut.createUploadSession(USER_ID, request);

        var captor = ArgumentCaptor.forClass(Document.class);
        verify(mockDocumentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getLast().getStorageKey()).isEqualTo("documents/%s/file.pdf".formatted(DOCUMENT_ID));
    }

    @Test
    void createUploadSession_text_queuesAndDispatches() {
        var user = createUser();
        var request = new DocumentUploadSessionRequest("TEXT", null, "Some pasted text");

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);

        var response = sut.createUploadSession(USER_ID, request);

        assertThat(response.document().status()).isEqualTo("QUEUED");
        assertThat(response.uploadTarget()).isNull();
        verify(mockDocumentProcessingDispatcher).dispatch(DOCUMENT_ID);
    }

    @Test
    void finaliseUpload_pdfWithoutStoredObject_rejects() {
        var document = createDocument(InputType.PDF, DocumentStatus.PENDING_UPLOAD);
        document.setStorageKey("documents/%s/file.pdf".formatted(DOCUMENT_ID));

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));
        when(mockStorageService.exists(document.getStorageKey())).thenReturn(CompletableFuture.completedFuture(false));

        assertThatThrownBy(() -> sut.finaliseUpload(USER_ID, DOCUMENT_ID))
                .isInstanceOf(DocumentNotReadyException.class)
                .hasMessageContaining("has not been uploaded yet");

        verify(mockDocumentProcessingDispatcher, never()).dispatch(any(UUID.class));
    }

    @Test
    void finaliseUpload_pdfWithStoredObject_queuesAndDispatches() {
        var document = createDocument(InputType.PDF, DocumentStatus.UPLOADING);
        document.setStorageKey("documents/%s/file.pdf".formatted(DOCUMENT_ID));

        // After the CAS update succeeds, finaliseUpload re-fetches the document
        // to get a fresh entity. Simulate the re-fetch returning QUEUED status.
        var refetchedDocument = createDocument(InputType.PDF, DocumentStatus.QUEUED);
        refetchedDocument.setStorageKey(document.getStorageKey());

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document), Optional.of(refetchedDocument));
        when(mockStorageService.exists(document.getStorageKey())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockDocumentRepository.transitionStatusIfCurrentStatusNotIn(
                DOCUMENT_ID,
                USER_ID,
                DocumentStatus.QUEUED,
                java.util.Set.of(DocumentStatus.QUEUED, DocumentStatus.PROCESSING, DocumentStatus.READY)))
                .thenReturn(1);

        var response = sut.finaliseUpload(USER_ID, DOCUMENT_ID);

        assertThat(response.status()).isEqualTo("QUEUED");
        verify(mockDocumentProcessingDispatcher).dispatch(DOCUMENT_ID);
        verify(mockDocumentStatusNotifier).notify(DOCUMENT_ID, ProcessingStatusEvent.queued());
    }

    @Test
    void finaliseUpload_whenAnotherRequestAlreadyQueued_doesNotDispatchAgain() {
        var document = createDocument(InputType.PDF, DocumentStatus.UPLOADING);
        document.setStorageKey("documents/%s/file.pdf".formatted(DOCUMENT_ID));
        var queuedDocument = createDocument(InputType.PDF, DocumentStatus.QUEUED);
        queuedDocument.setStorageKey(document.getStorageKey());

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID))
                .thenReturn(Optional.of(document), Optional.of(queuedDocument));
        when(mockStorageService.exists(document.getStorageKey())).thenReturn(CompletableFuture.completedFuture(true));
        when(mockDocumentRepository.transitionStatusIfCurrentStatusNotIn(
                DOCUMENT_ID,
                USER_ID,
                DocumentStatus.QUEUED,
                java.util.Set.of(DocumentStatus.QUEUED, DocumentStatus.PROCESSING, DocumentStatus.READY)))
                .thenReturn(0);

        var response = sut.finaliseUpload(USER_ID, DOCUMENT_ID);

        assertThat(response.status()).isEqualTo("QUEUED");
        verify(mockDocumentProcessingDispatcher, never()).dispatch(any(UUID.class));
        verify(mockDocumentStatusNotifier, never()).notify(DOCUMENT_ID, ProcessingStatusEvent.queued());
    }

    @Test
    void uploadContent_happyPath_storesContentAndReturnsResponse() {
        var document = createDocument(InputType.PDF, DocumentStatus.PENDING_UPLOAD);

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));
        when(mockStorageService.store(eq(DOCUMENT_ID), any()))
                .thenReturn(CompletableFuture.completedFuture("documents/%s/abc123".formatted(DOCUMENT_ID)));

        var response = sut.uploadContent(USER_ID, DOCUMENT_ID, "pdf-bytes".getBytes());

        assertThat(response.id()).isEqualTo(DOCUMENT_ID);
        assertThat(document.getStorageKey()).isEqualTo("documents/%s/abc123".formatted(DOCUMENT_ID));
        verify(mockDocumentStatusNotifier).notify(DOCUMENT_ID, ProcessingStatusEvent.uploading());
        verify(mockDocumentRepository, atLeastOnce()).save(document);
    }

    @Test
    void uploadContent_nonPdfDocument_rejects() {
        var document = createDocument(InputType.TEXT, DocumentStatus.QUEUED);

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> sut.uploadContent(USER_ID, DOCUMENT_ID, "content".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only PDF");
    }

    @Test
    void uploadContent_documentAlreadyProcessing_rejects() {
        var document = createDocument(InputType.PDF, DocumentStatus.PROCESSING);

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> sut.uploadContent(USER_ID, DOCUMENT_ID, "content".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot upload content");
    }

    @Test
    void uploadContent_documentAlreadyReady_rejects() {
        var document = createDocument(InputType.PDF, DocumentStatus.READY);

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> sut.uploadContent(USER_ID, DOCUMENT_ID, "content".getBytes()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot upload content");
    }

    @Test
    void finaliseUpload_pdfWithNullStorageKey_rejects() {
        var document = createDocument(InputType.PDF, DocumentStatus.PENDING_UPLOAD);
        // storageKey is null by default

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> sut.finaliseUpload(USER_ID, DOCUMENT_ID))
                .isInstanceOf(DocumentNotReadyException.class)
                .hasMessageContaining("has not been uploaded yet");

        verify(mockDocumentProcessingDispatcher, never()).dispatch(any(UUID.class));
    }

    @Test
    void finaliseUpload_textWithMissingText_rejects() {
        var document = createDocument(InputType.TEXT, DocumentStatus.PENDING_UPLOAD);
        document.setExtractedText(null);

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> sut.finaliseUpload(USER_ID, DOCUMENT_ID))
                .isInstanceOf(DocumentNotReadyException.class)
                .hasMessageContaining("Text content is required");
    }

    @Test
    void createUploadSession_unsupportedInputType_rejects() {
        var request = new DocumentUploadSessionRequest("INVALID", null, null);

        assertThatThrownBy(() -> sut.createUploadSession(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported input type");
    }

    @Test
    void createUploadSession_imageInputType_rejects() {
        var request = new DocumentUploadSessionRequest("IMAGE", null, null);

        assertThatThrownBy(() -> sut.createUploadSession(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not yet supported");
    }

    @Test
    void createUploadSession_textWithBlankContent_rejects() {
        var request = new DocumentUploadSessionRequest("TEXT", null, "   ");
        var user = createUser();
        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> sut.createUploadSession(USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must include content");
    }

    @Test
    void createUploadSession_pdf_whenUploadTargetFails_cleansUpDocument() {
        var user = createUser();
        var request = new DocumentUploadSessionRequest("PDF", "contract.pdf", null);

        when(mockUserRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(mockDocumentUploadTargetProvider.createUploadTarget(eq(DOCUMENT_ID), eq("contract.pdf")))
                .thenThrow(new RuntimeException("S3 presigning failed"));

        assertThatThrownBy(() -> sut.createUploadSession(USER_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("S3 presigning failed");

        // Document should be cleaned up (deleted)
        verify(mockDocumentRepository).delete(any(Document.class));
    }

    @Test
    void uploadContent_whenStorageFails_notifiesFailureAndCompletesStream() {
        var document = createDocument(InputType.PDF, DocumentStatus.PENDING_UPLOAD);

        when(mockDocumentRepository.findByIdAndUserId(DOCUMENT_ID, USER_ID)).thenReturn(Optional.of(document));
        when(mockStorageService.store(eq(DOCUMENT_ID), any()))
                .thenReturn(CompletableFuture.failedFuture(new StorageSaveException("Disk full")));

        assertThatThrownBy(() -> sut.uploadContent(USER_ID, DOCUMENT_ID, "pdf".getBytes()))
                .isInstanceOf(StorageSaveException.class)
                .hasMessageContaining("Disk full");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).contains("Disk full");
        verify(mockDocumentStatusNotifier).notify(DOCUMENT_ID, ProcessingStatusEvent.uploading());
        verify(mockDocumentStatusNotifier).notify(
                eq(DOCUMENT_ID),
                eq(ProcessingStatusEvent.failed("Failed to store file: Disk full")));
        verify(mockDocumentStatusNotifier).complete(DOCUMENT_ID);
    }

    private static User createUser() {
        var user = new User();
        user.setEmail("test@example.com");
        user.setDisplayName("Test User");
        return user;
    }

    private static Document createDocument(InputType inputType, DocumentStatus status) {
        var document = new Document();
        setDocumentId(document, DOCUMENT_ID);
        document.setInputType(inputType);
        document.setStatus(status);
        document.setOriginalFilename("contract.pdf");
        return document;
    }

    private static DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType() != null ? document.getDocumentType().toString() : null,
                document.getInputType().toString(),
                document.getOriginalFilename(),
                document.getStatus().toString(),
                document.getErrorMessage(),
                null,
                Instant.now());
    }

    private static void setDocumentId(Document document, UUID documentId) {
        try {
            Field idField = Document.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(document, documentId);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
