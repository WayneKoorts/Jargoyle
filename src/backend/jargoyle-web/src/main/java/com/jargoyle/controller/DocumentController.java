package com.jargoyle.controller;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.jargoyle.SseEmitterRegistry;
import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentService;
import com.jargoyle.service.DocumentUploadRequest;
import com.jargoyle.service.storage.StorageSaveException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final SseEmitterRegistry sseEmitterRegistry;

    public DocumentController(
        DocumentService documentService,
        SseEmitterRegistry sseEmitterRegistry) {

        this.documentService = documentService;
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
        
        // Get the requested document as an existence check.
        documentService.getById(user.getId(), documentId);

        var emitter = new SseEmitter(60_000L);
        sseEmitterRegistry.register(documentId, emitter);

        return emitter;
    }

    @PostMapping()
    public ResponseEntity<DocumentResponse> upload(
        @CurrentUser User user,
        @RequestPart Optional<MultipartFile> file,
        @RequestParam Optional<String> fileName,
        @RequestParam Optional<String> text) throws StorageSaveException, IOException {
        
        if (file.isPresent() && text.isPresent()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Must provide either file or text, but not both");
        } else if (!file.isPresent() && !text.isPresent()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Must provide either the \"file\" or \"text\" parameters");
        }

        DocumentResponse createdDocument;
        if (file.isPresent()) {
            var uploadRequest = new DocumentUploadRequest.PdfDocumentUpload(
                user.getId(), fileName.orElse(""), file.get().getBytes());
            createdDocument = documentService.upload(uploadRequest);
        } else {
            var uploadRequest = new DocumentUploadRequest.TextDocumentUpload(user.getId(), text.get());
            createdDocument = documentService.upload(uploadRequest);
        }

        return ResponseEntity.accepted().body(createdDocument);
    }

}
