package com.jargoyle.service.exception;

import java.util.UUID;

public class DocumentProcessingException extends RuntimeException {

    private static final String errorFormat = "An error occurred during document processing for document \"%s\"";

    public DocumentProcessingException(String message) {
        super(message);
    }

    public DocumentProcessingException(UUID documentId) {
        super(String.format(errorFormat, documentId));
    }

    public DocumentProcessingException(UUID documentId, Throwable cause) {
        super(String.format(errorFormat, documentId), cause);
    }
}
