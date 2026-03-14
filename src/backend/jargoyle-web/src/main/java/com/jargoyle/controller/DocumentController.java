package com.jargoyle.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.jargoyle.SseEmitterRegistry;
import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.dto.DocumentResponse;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentService;
import com.jargoyle.service.DocumentStatusNotifier;

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

    @GetMapping("/")
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

}
