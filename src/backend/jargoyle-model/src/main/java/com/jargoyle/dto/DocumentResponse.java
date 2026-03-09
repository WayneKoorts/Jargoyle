package com.jargoyle.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String title,
    String documentType,
    String inputType,
    String originalFilename,
    String status,
    String errorMessage,
    DocumentSummaryResponse summary,
    Instant createdAt
) {}
