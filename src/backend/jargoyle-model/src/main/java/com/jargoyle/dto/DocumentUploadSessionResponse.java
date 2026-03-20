package com.jargoyle.dto;

import java.util.Objects;

public record DocumentUploadSessionResponse(
    DocumentResponse document,
    DocumentUploadTargetResponse uploadTarget
) {
    public DocumentUploadSessionResponse {
        Objects.requireNonNull(document, "document must not be null");
    }
}
