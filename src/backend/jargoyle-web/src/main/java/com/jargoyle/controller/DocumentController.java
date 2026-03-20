package com.jargoyle.controller;

import java.io.IOException;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
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
import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.dto.DocumentUploadSessionRequest;
import com.jargoyle.dto.DocumentUploadSessionResponse;
import com.jargoyle.dto.DocumentUpdateRequest;
import com.jargoyle.dto.ProcessingStatusEvent;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentIngestionService;
import com.jargoyle.service.DocumentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentIngestionService documentIngestionService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public DocumentController(
        DocumentService documentService,
        DocumentIngestionService documentIngestionService,
        SseEmitterRegistry sseEmitterRegistry) {

        this.documentService = documentService;
        this.documentIngestionService = documentIngestionService;
        this.sseEmitterRegistry = sseEmitterRegistry;
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
        var emitter = new SseEmitter(300_000L);
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

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
        @CurrentUser User user,
        @PathVariable UUID documentId) {
        
        documentService.delete(user.getId(), documentId);

        return ResponseEntity.noContent().build();
    }

}
