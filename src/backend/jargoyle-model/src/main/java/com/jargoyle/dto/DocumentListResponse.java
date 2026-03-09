package com.jargoyle.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Slimmed-down version of {@link com.jargoyle.dto.DocumentResponse DocumentResponse} that only
 * provides summary text and metadata.
 */
public record DocumentListResponse(
    UUID id,
    String title,
    String documentType,
    String inputType,
    String status,
    Instant createdAt
) {}
