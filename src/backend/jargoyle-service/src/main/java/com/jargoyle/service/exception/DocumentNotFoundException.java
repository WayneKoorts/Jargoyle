package com.jargoyle.service.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {

    private static final String errorFormat = "Document with ID \"%s\" not found";

    public DocumentNotFoundException(String message) {
        super(message);
    }

    public DocumentNotFoundException(UUID documentId) {
        super(String.format(errorFormat, documentId));
    }

    public DocumentNotFoundException(UUID documentId, Throwable cause) {
        super(String.format(errorFormat, documentId), cause);
    }
}
