package com.jargoyle.service;

import java.util.UUID;

public sealed interface DocumentUploadRequest {
    record PdfDocumentUpload(UUID userId, String originalFilename, byte[] content) implements DocumentUploadRequest {}
    record TextDocumentUpload(UUID userId, String pastedText) implements DocumentUploadRequest {}
}
