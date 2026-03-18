package com.jargoyle.service.exception;

import java.util.UUID;

public class DocumentNotReadyException extends RuntimeException {

    private static final String errorFormat = "Document with ID \"%s\" is not ready for this operation";

    public DocumentNotReadyException(String message) {
        super(message);
    }

    public DocumentNotReadyException(UUID documentId) {
        super(String.format(errorFormat, documentId));
    }

    public DocumentNotReadyException(UUID documentId, Throwable cause) {
        super(String.format(errorFormat, documentId), cause);
    }
}
