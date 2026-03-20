package com.jargoyle.dto;

import java.util.Objects;

public record DocumentUploadTargetResponse(
    String url,
    String method
) {
    public DocumentUploadTargetResponse {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(method, "method must not be null");
        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }
}
