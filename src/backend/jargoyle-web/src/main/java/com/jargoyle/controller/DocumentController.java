package com.jargoyle.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.jargoyle.SseEmitterRegistry;
import com.jargoyle.config.properties.MvcAsyncProperties;
import com.jargoyle.dto.DocumentContentLocationResponse;
import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentUploadSessionRequest;
import com.jargoyle.dto.DocumentUploadSessionResponse;
import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.entity.InputType;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentIngestionService;
import com.jargoyle.service.DocumentService;
import com.jargoyle.service.storage.StorageService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIngestionService documentIngestionService;
    private final SseEmitterRegistry sseEmitterRegistry;
    private final StorageService storageService;
    private final MvcAsyncProperties mvcAsyncProperties;

    public DocumentController(
        DocumentService documentService,
        DocumentIngestionService documentIngestionService,
        SseEmitterRegistry sseEmitterRegistry,
        StorageService storageService,
        MvcAsyncProperties mvcAsyncProperties) {

        this.documentService = documentService;
        this.documentIngestionService = documentIngestionService;
        this.sseEmitterRegistry = sseEmitterRegistry;
        this.storageService = storageService;
        this.mvcAsyncProperties = mvcAsyncProperties;
    }

    @GetMapping()
    public ResponseEntity<Page<DocumentListResponse>> list(
        @CurrentUser User user,
        Pageable pageable) {

        var docList = documentService.list(user.getId(), pageable);

        return ResponseEntity.ok(docList);
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> get(
        @CurrentUser User user,
        @PathVariable UUID documentId) {
        
        var document = documentService.getById(user.getId(), documentId);

        return ResponseEntity.ok(document);
    }

    @GetMapping(path = "/{documentId}/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(
        @CurrentUser User user,
        @PathVariable UUID documentId) {

        var document = documentService.getById(user.getId(), documentId);
        var emitter = new SseEmitter(mvcAsyncProperties.timeoutMillis());
        sseEmitterRegistry.register(documentId, emitter);

        try {
            emitter.send(ProcessingStatusEvent.fromDocumentStatus(
                com.jargoyle.entity.DocumentStatus.valueOf(document.status()),
                document.errorMessage()));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }

        return emitter;
    }

    @PostMapping("/uploads")
    public ResponseEntity<DocumentUploadSessionResponse> createUploadSession(
        @CurrentUser User user,
        @Valid @RequestBody DocumentUploadSessionRequest request) {

        var response = documentIngestionService.createUploadSession(user.getId(), request);
        return ResponseEntity.accepted().body(response);
    }

    @PutMapping(path = "/{documentId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadContent(
        @CurrentUser User user,
        @PathVariable UUID documentId,
        @RequestPart MultipartFile file) throws IOException {

        var document = documentIngestionService.uploadContent(user.getId(), documentId, file.getBytes());
        return ResponseEntity.ok(document);
    }

    @PostMapping("/{documentId}/finalise")
    public ResponseEntity<DocumentResponse> finaliseUpload(
        @CurrentUser User user,
        @PathVariable UUID documentId) {

        var document = documentIngestionService.finaliseUpload(user.getId(), documentId);
        return ResponseEntity.accepted().body(document);
    }

    @PatchMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> update(
        @CurrentUser User user,
        @PathVariable UUID documentId,
        @Valid @RequestBody DocumentUpdateRequest updateRequest) {

        var updatedDoc = documentService.update(user.getId(), documentId, updateRequest);

        return ResponseEntity.ok(updatedDoc);
    }

    /**
     * Returns the location from which the frontend can access the original document content.
     *
     * <p>For file-based documents (PDF, IMAGE), the response contains a URL — a presigned
     * S3 URL in production or a backend-relative URL in local development. For TEXT
     * documents, the response contains the inline text content.</p>
     */
    @GetMapping("/{documentId}/original")
    public ResponseEntity<DocumentContentLocationResponse> getOriginalContentLocation(
        @CurrentUser User user,
        @PathVariable UUID documentId) {

        var location = documentService.getContentLocation(user.getId(), documentId);
        return ResponseEntity.ok(location);
    }

    /**
     * Streams the raw file content of a document for inline viewing.
     *
     * <p>This endpoint is used by the local development profile, where the
     * {@link com.jargoyle.service.content.local.LocalDocumentContentTargetProvider}
     * returns a URL pointing here. In production, the browser fetches directly
     * from S3 via a presigned URL and this endpoint is not called.</p>
     */
    @GetMapping("/{documentId}/original/stream")
    public ResponseEntity<Resource> streamOriginalContent(
        @CurrentUser User user,
        @PathVariable UUID documentId) {

        var document = documentService.getDocumentEntity(user.getId(), documentId);

        // TEXT documents return the extracted text as bytes
        if (document.getInputType() == InputType.TEXT) {
            var textBytes = (document.getExtractedText() != null ? document.getExtractedText() : "")
                    .getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                    .body(new ByteArrayResource(textBytes));
        }

        // PDF/IMAGE — load from storage and serve with the appropriate content type
        var storageKey = document.getStorageKey();
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalStateException(
                    "Document %s has input type %s but no storage key".formatted(documentId, document.getInputType()));
        }

        var resource = storageService.load(storageKey).join();

        var contentType = resolveMediaType(document.getInputType(), document.getOriginalFilename());

        var contentDisposition = document.getOriginalFilename() != null
                ? ContentDisposition.inline().filename(document.getOriginalFilename()).build()
                : ContentDisposition.inline().build();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(resource);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
        @CurrentUser User user,
        @PathVariable UUID documentId) {
        
        documentService.delete(user.getId(), documentId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Determines the {@link MediaType} for a document based on its input type and filename.
     *
     * <p>PDF documents always return {@code application/pdf}. IMAGE documents attempt
     * to infer the media type from the original filename (e.g. {@code .png} →
     * {@code image/png}), falling back to {@code application/octet-stream}.</p>
     */
    private static MediaType resolveMediaType(InputType inputType, String originalFilename) {
        return switch (inputType) {
            case PDF -> MediaType.APPLICATION_PDF;
            case IMAGE -> originalFilename != null
                    ? MediaTypeFactory.getMediaType(originalFilename).orElse(MediaType.APPLICATION_OCTET_STREAM)
                    : MediaType.APPLICATION_OCTET_STREAM;
            case TEXT -> new MediaType("text", "plain", StandardCharsets.UTF_8);
        };
    }
}
