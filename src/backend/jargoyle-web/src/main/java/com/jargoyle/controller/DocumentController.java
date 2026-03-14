package com.jargoyle.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jargoyle.dto.DocumentListResponse;
import com.jargoyle.entity.User;
import com.jargoyle.service.DocumentService;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(
        DocumentService documentService) {

        this.documentService = documentService;
    }

    @GetMapping()
    public ResponseEntity<Page<DocumentListResponse>> list(
        @CurrentUser User user,
        Pageable pageable) {

        var docList = documentService.list(user.getId(), pageable);

        return ResponseEntity.ok(docList);
    }




}
